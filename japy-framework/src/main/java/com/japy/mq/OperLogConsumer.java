package com.japy.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.module.system.entity.SysOperLog;
import com.japy.module.system.mapper.SysOperLogMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 操作日志消费者：订阅 japy_oper_log → 反序列化 → 落库。
 * - 探测消息（tag=probe）仅验证通路不落库；
 * - 重复消费（MQ 重放）由 trace_id 唯一索引幂等忽略；
 * - 启动失败不阻断应用（生产者侧多级降级兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperLogConsumer {

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer-group:japy-framework-consumer}")
    private String consumerGroup;

    @Value("${rocketmq.topics.oper-log:japy_oper_log}")
    private String operLogTopic;

    @PostConstruct
    public void start() {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        try {
            consumer.subscribe(operLogTopic, "*");
            consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                for (MessageExt msg : msgs) {
                    try {
                        // 探测消息（MQ 恢复探针）：仅验证通路，不落库
                        if (LogDeliveryService.TAG_PROBE.equals(msg.getTags())) {
                            continue;
                        }
                        SysOperLog logEntity = objectMapper.readValue(
                                new String(msg.getBody(), StandardCharsets.UTF_8), SysOperLog.class);
                        try {
                            operLogMapper.insert(logEntity);
                        } catch (DuplicateKeyException e) {
                            // 幂等：trace_id 唯一索引冲突 = 该日志已消费过（MQ 重放场景），忽略
                            log.debug("操作日志重复消费忽略 traceId={}", logEntity.getTraceId());
                        }
                    } catch (Exception e) {
                        log.error("操作日志消费失败", e);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
            log.info("RocketMQ 操作日志消费者启动成功: {} topic={}", nameServer, operLogTopic);
        } catch (Exception e) {
            log.warn("RocketMQ 消费者启动失败（日志降级同步落库）: nameServer={} msg={}", nameServer, e.getMessage());
        }
    }
}
