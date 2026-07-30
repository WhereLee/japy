package com.recloud.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recloud.common.entity.OperationLog;
import com.recloud.common.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 操作日志 MQ 消费者
 * <p>
 * 消费 operation-log-topic 消息，异步写入数据库。
 * 使用 CLUSTERING 模式，支持并发消费。
 * <p>
 * 条件装配：仅在 rocketmq.consumer.enabled=true 时启用。
 * RocketMQ 不可用时（如本地开发），OperationLogProducer 会自动降级到 Redis List / 直接写 DB。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.consumer.enabled", havingValue = "true", matchIfMissing = false)
@RocketMQMessageListener(
        topic = "operation-log-topic",
        consumerGroup = "recloud-operation-log-consumer",
        consumeThreadMax = 20
)
public class OperationLogConsumer implements RocketMQListener<String> {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            OperationLog operationLog = objectMapper.readValue(message, OperationLog.class);
            operationLogMapper.insert(operationLog);
            log.debug("操作日志已写入数据库: module={}, operation={}", 
                    operationLog.getModule(), operationLog.getOperation());
        } catch (Exception e) {
            log.error("消费操作日志失败: {}", e.getMessage(), e);
        }
    }
}
