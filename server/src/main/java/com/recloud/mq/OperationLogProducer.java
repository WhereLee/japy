package com.recloud.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recloud.common.entity.OperationLog;
import com.recloud.common.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 操作日志 MQ 生产者（三级降级策略）
 * <p>
 * 降级链路：MQ → Redis List → DB 同步写入
 * <p>
 * 设计思路：
 * 1. 优先发 MQ（异步，不阻塞主流程）
 * 2. MQ 挂了 → 写入 Redis List 作为临时队列
 * 3. Redis 也挂了 → 直接同步写 DB（最后的兜底）
 * 4. 定时任务每 30s 尝试将 Redis List 中的日志重发到 MQ
 * <p>
 * 为什么需要降级？
 * - 操作日志是审计依据，不能丢
 * - MQ 不是核心组件，挂了不能影响业务
 * - Redis 也不是核心组件，挂了不能影响业务
 * - 只有 DB 是核心组件，DB 挂了才应该报错
 * <p>
 * 面试价值：
 * - 展示“多级降级”思维：不是非此即彼，而是层层兜底
 * - 展示“数据不丢失”原则：日志类数据必须有保底方案
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final OperationLogMapper operationLogMapper;

    private static final String TOPIC = "operation-log-topic";
    private static final String FALLBACK_KEY = "log:fallback";
    /** Redis List 最大堆积量，超过后直接写 DB（防止 Redis 内存爆） */
    private static final long MAX_FALLBACK_SIZE = 500;

    /**
     * 发送操作日志（三级降级）
     */
    public void sendOperationLog(OperationLog operationLog) {
        try {
            String json = objectMapper.writeValueAsString(operationLog);

            // 第一级：发 MQ
            rocketMQTemplate.sendOneWay(TOPIC, MessageBuilder.withPayload(json).build());
            log.debug("操作日志已发送到MQ: module={}, operation={}",
                    operationLog.getModule(), operationLog.getOperation());

        } catch (Exception e) {
            log.warn("MQ 发送失败，尝试 Redis 暂存: {}", e.getMessage());

            // 第二级：写 Redis List
            try {
                String json = objectMapper.writeValueAsString(operationLog);
                redisTemplate.opsForList().leftPush(FALLBACK_KEY, json);

                // 防止 Redis 堆积过多（超过阈值时主动消费）
                Long size = redisTemplate.opsForList().size(FALLBACK_KEY);
                if (size != null && size > MAX_FALLBACK_SIZE) {
                    log.warn("Redis 日志堆积超过 {}，触发主动消费", MAX_FALLBACK_SIZE);
                    drainFallbackToDb();
                }
            } catch (Exception redisEx) {
                log.warn("Redis 暂存也失败，降级到 DB 同步写入: {}", redisEx.getMessage());

                // 第三级：直接同步写 DB
                try {
                    operationLogMapper.insert(operationLog);
                    log.info("操作日志已同步写入DB（兜底）: module={}", operationLog.getModule());
                } catch (Exception dbEx) {
                    // DB 也挂了 → 这条日志只能丢了，记录 ERROR
                    log.error("操作日志彻底丢失: module={}, error={}",
                            operationLog.getModule(), dbEx.getMessage());
                }
            }
        }
    }

    /**
     * 定时任务：每 30s 尝试将 Redis 暂存的日志重发到 MQ
     * <p>
     * MQ 恢复后，自动将暂存的日志补发出去。
     */
    @Scheduled(fixedDelay = 30_000)
    public void retryFallbackLogs() {
        try {
            Long size = redisTemplate.opsForList().size(FALLBACK_KEY);
            if (size == null || size == 0) return;

            log.info("开始重发暂存的操作日志，堆积数量: {}", size);
            int successCount = 0;

            for (long i = 0; i < size; i++) {
                String json = redisTemplate.opsForList().rightPop(FALLBACK_KEY);
                if (json == null) break;

                try {
                    rocketMQTemplate.sendOneWay(TOPIC, MessageBuilder.withPayload(json).build());
                    successCount++;
                } catch (Exception e) {
                    // MQ 还没恢复，放回去
                    redisTemplate.opsForList().leftPush(FALLBACK_KEY, json);
                    break;
                }
            }

            if (successCount > 0) {
                log.info("重发暂存日志完成: 成功 {} 条", successCount);
            }
        } catch (Exception e) {
            log.warn("重发暂存日志失败（Redis 不可用）: {}", e.getMessage());
            // Redis 也挂了，尝试直接写 DB
            drainFallbackToDb();
        }
    }

    /**
     * 将 Redis 暂存的日志直接写入 DB（最终兜底）
     */
    private void drainFallbackToDb() {
        try {
            while (true) {
                String json = redisTemplate.opsForList().rightPop(FALLBACK_KEY);
                if (json == null) break;
                OperationLog logEntry = objectMapper.readValue(json, OperationLog.class);
                operationLogMapper.insert(logEntry);
            }
        } catch (Exception e) {
            log.warn("drainFallbackToDb 失败: {}", e.getMessage());
        }
    }
}
