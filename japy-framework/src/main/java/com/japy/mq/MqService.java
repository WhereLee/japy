package com.japy.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * MQ 发送服务：操作日志等异步消息。
 * 发送失败返回 false（调用方降级同步处理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqService {

    private final DefaultMQProducer producer;

    public boolean send(String topic, String tag, String body) {
        try {
            Message msg = new Message(topic, tag, body.getBytes(StandardCharsets.UTF_8));
            producer.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("MQ 发送失败 topic={} tag={}: {}", topic, tag, e.getMessage());
            return false;
        }
    }
}
