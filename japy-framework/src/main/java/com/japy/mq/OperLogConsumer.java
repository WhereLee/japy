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
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 操作日志消费者：订阅 japy_oper_log → 反序列化 → 落库。
 * 启动失败不阻断应用（降级由生产者侧同步落库兜底）。
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
                        SysOperLog logEntity = objectMapper.readValue(
                                new String(msg.getBody(), StandardCharsets.UTF_8), SysOperLog.class);
                        operLogMapper.insert(logEntity);
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
            log.warn("RocketMQ 消费者启动失败（日志降级同步落库）: {}", e.getMessage());
        }
    }
}
