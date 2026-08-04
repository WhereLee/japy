package com.japy.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 日志多级降级投递服务（面试亮点：消息中间件抽象 + 降级 + 动态恢复 + 幂等）。
 *
 * 投递路径（L0 → L1 → 兜底）：
 *   1. RocketMQ（主通道）——RocketMqSender
 *   2. Redis Stream（备通道）——RocketMQ 挂掉时消息缓冲到 Redis（持久、不丢）
 *   3. 同步落库（最后兜底）——两级通道都不可用，由调用方（OperLogAspect）直接写库
 *
 * 恢复机制：定时探测 MQ 是否恢复（发送 probe 消息，Consumer 端丢弃）；
 *   恢复后把 Redis Stream 中积压的消息重放回 MQ，消费落库，Stream 记录删除。
 * 幂等保障：操作日志携带 trace_id，sys_oper_log 唯一索引，重复消费自动忽略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogDeliveryService {

    /** 探测消息标签：仅用于探测 MQ 可用性，Consumer 端丢弃 */
    public static final String TAG_PROBE = "probe";
    /** 正常日志消息标签 */
    public static final String TAG_OPER = "oper";

    private static final String LOCK_KEY = "japy:task:mq-recover";
    private static final long BATCH_SIZE = 100;

    private final RocketMqSender mqSender;
    private final RedisStreamSender streamSender;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    @Value("${rocketmq.topics.oper-log:japy_oper_log}")
    private String operLogTopic;
    @Value("${mq.fallback.stream-key:japy:log:fallback-stream}")
    private String streamKey;

    /** MQ 健康标记：每次发送失败置 false，探测成功置 true */
    private volatile boolean mqHealthy = true;

    /**
     * 投递一条日志：主 MQ → 备 Stream → false（调用方同步落库兜底）。
     */
    public boolean send(String topic, String tag, String body) {
        if (mqHealthy && mqSender.send(topic, tag, body)) {
            return true;
        }
        // MQ 不可用：标记降级，缓冲到 Redis Stream（持久不丢）
        if (mqHealthy) {
            mqHealthy = false;
            log.warn("RocketMQ 不可用，操作日志降级写入 Redis Stream (key={})", streamKey);
        }
        return streamSender.send(topic, tag, body);
    }

    /**
     * 定时探测 MQ 恢复（多实例分布式锁）：恢复后重放 Stream 积压。
     */
    @Scheduled(fixedDelayString = "${mq.fallback.probe-interval-ms:60000}")
    public void probeAndRecover() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            if (!lock.tryLock(3, 0, TimeUnit.SECONDS)) {
                return;
            }
            if (mqHealthy) {
                return; // 未处于降级态，无需探测
            }
            if (!mqSender.send(operLogTopic, TAG_PROBE, "{}")) {
                return; // MQ 仍未恢复
            }
            mqHealthy = true;
            log.info("RocketMQ 已恢复，开始重放 Redis Stream 积压日志");
            drainStream();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 重放 Redis Stream 积压消息到 MQ（成功即删记录；失败保留下轮重试） */
    private void drainStream() {
        int replayed = 0;
        while (true) {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .range(streamKey, Range.unbounded());
            if (records == null || records.isEmpty()) {
                break;
            }
            boolean progressed = false;
            for (MapRecord<String, Object, Object> record : records) {
                Map<Object, Object> value = record.getValue();
                String tag = value.get("tag") == null ? TAG_OPER : String.valueOf(value.get("tag"));
                String payload = value.get("payload") == null ? "" : String.valueOf(value.get("payload"));
                if (mqSender.send(operLogTopic, tag, payload)) {
                    redisTemplate.opsForStream().delete(streamKey, record.getId());
                    replayed++;
                    progressed = true;
                }
            }
            if (!progressed) {
                break; // 重发失败，下轮再试，避免死循环
            }
        }
        if (replayed > 0) {
            log.info("Redis Stream 积压日志重放完成：{} 条", replayed);
        }
    }

    /** MQ 当前是否健康（供监控/测试断言） */
    public boolean isMqHealthy() {
        return mqHealthy;
    }
}
