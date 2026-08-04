package com.japy.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ 主通道（L0 优先级最高）：发送失败返回 false，由 LogDeliveryService 降级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RocketMqSender implements LogMessageSender {

    private final DefaultMQProducer producer;

    @Override
    public boolean send(String topic, String tag, String body) {
        try {
            Message msg = new Message(topic, tag, body.getBytes(StandardCharsets.UTF_8));
            producer.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("RocketMQ 发送失败 topic={} tag={}: {}", topic, tag, e.getMessage());
            return false;
        }
    }
}
