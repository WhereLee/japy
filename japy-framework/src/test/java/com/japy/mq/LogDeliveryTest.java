package com.japy.mq;

import com.japy.base.AbstractIntegrationTest;
import com.japy.module.ai.monitor.MonitorScheduler;
import com.japy.module.system.entity.SysOperLog;
import com.japy.module.system.mapper.SysOperLogMapper;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 日志多级降级链路测试（面试亮点验证）：
 * 1. MQ 可用 → 走 RocketMQ（Stream 无数据）
 * 2. MQ 挂 → 降级 Redis Stream（消息缓冲不丢）
 * 3. MQ 恢复 → 积压重放回 MQ 并清空 Stream
 * 4. trace_id 唯一索引幂等（重复落库被拒）
 *
 * 通过 @MockBean 替换 DefaultMQProducer：测试完全自包含，不依赖真实 MQ。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class LogDeliveryTest extends AbstractIntegrationTest {

    @Autowired
    private LogDeliveryService deliveryService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private SysOperLogMapper operLogMapper;

    @MockBean
    private DefaultMQProducer producer;
    /** 禁用 AI 监测调度器，避免后台任务干扰 */
    @MockBean
    private MonitorScheduler monitorScheduler;

    @Value("${mq.fallback.stream-key:japy:log:fallback-stream}")
    private String streamKey;

    @BeforeEach
    void cleanStream() {
        // 其他测试类的操作日志可能真实降级进 Stream（MQ 未运行时），每个用例前清理保证独立
        redisTemplate.delete(streamKey);
        deliveryService.setMqHealthy(true);
    }

    @AfterEach
    void cleanup() {
        redisTemplate.delete(streamKey);          // Redis 不走事务，手动清理
        deliveryService.setMqHealthy(true);       // 重置健康标记
    }

    @Test
    @Order(1)
    void MQ可用走MQ通道Stream无积压() throws Exception {
        when(producer.send(any(Message.class))).thenReturn(null); // mock 成功（默认即成功）
        boolean ok = deliveryService.send("japy_oper_log", LogDeliveryService.TAG_OPER, "{\"traceId\":\"ok-1\"}");
        assertTrue(ok, "MQ 可用时应投递成功");
        assertTrue(deliveryService.isMqHealthy(), "健康标记应保持 true");
        assertEquals(0, streamSize(), "MQ 可用时 Redis Stream 不应有积压");
    }

    @Test
    @Order(2)
    void MQ挂降级RedisStream缓冲不丢() throws Exception {
        when(producer.send(any(Message.class)))
                .thenThrow(new MQClientException("mock: broker down", null));
        boolean ok = deliveryService.send("japy_oper_log", LogDeliveryService.TAG_OPER, "{\"traceId\":\"fb-1\"}");
        assertTrue(ok, "MQ 挂掉时降级 Stream 应返回成功（不丢消息）");
        assertFalse(deliveryService.isMqHealthy(), "MQ 故障应标记降级态");
        assertEquals(1, streamSize(), "消息应缓冲到 Redis Stream");
        // 再次发送仍走降级
        assertTrue(deliveryService.send("japy_oper_log", LogDeliveryService.TAG_OPER, "{\"traceId\":\"fb-2\"}"));
        assertEquals(2, streamSize(), "降级期间消息持续累积");
    }

    @Test
    @Order(3)
    void MQ恢复后积压重放并清空() throws Exception {
        // 制造降级：2 条消息进 Stream
        when(producer.send(any(Message.class)))
                .thenThrow(new MQClientException("mock: broker down", null));
        deliveryService.send("japy_oper_log", LogDeliveryService.TAG_OPER, "{\"traceId\":\"re-1\"}");
        deliveryService.send("japy_oper_log", LogDeliveryService.TAG_OPER, "{\"traceId\":\"re-2\"}");
        assertEquals(2, streamSize(), "降级阶段应积压 2 条");

        // MQ 恢复：producer 正常 → 探测触发重放
        when(producer.send(any(Message.class))).thenReturn(null);
        deliveryService.probeAndRecover();
        assertTrue(deliveryService.isMqHealthy(), "探测后应恢复健康标记");
        assertEquals(0, streamSize(), "积压消息应全部重放并清空 Stream");
    }

    @Test
    @Order(4)
    void MQ未恢复时探测不动作() throws Exception {
        when(producer.send(any(Message.class)))
                .thenThrow(new MQClientException("mock: still down", null));
        deliveryService.send("japy_oper_log", LogDeliveryService.TAG_OPER, "{\"traceId\":\"nr-1\"}");
        deliveryService.probeAndRecover(); // 探测失败
        assertFalse(deliveryService.isMqHealthy(), "MQ 未恢复应保持降级态");
        assertEquals(1, streamSize(), "积压应保留等待下轮重试");
    }

    @Test
    @Order(5)
    void traceId唯一索引幂等() {
        SysOperLog first = new SysOperLog();
        first.setTraceId("idem-trace-1");
        first.setTitle("幂等测试");
        operLogMapper.insert(first);
        // 同 traceId 重复落库 → 唯一索引拒绝（幂等：重放/重复消费不产生重复日志）
        SysOperLog dup = new SysOperLog();
        dup.setTraceId("idem-trace-1");
        dup.setTitle("重复");
        assertThrows(DuplicateKeyException.class, () -> operLogMapper.insert(dup),
                "同 trace_id 重复落库应被唯一索引拒绝");
    }

    private long streamSize() {
        var records = redisTemplate.opsForStream().range(streamKey, Range.unbounded());
        return records == null ? 0 : records.size();
    }
}
