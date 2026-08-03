package com.japy.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ Producer 配置（手动封装，不依赖第三方 starter）。
 * 启动失败不阻断应用（log.warn），由 MqService 发送失败时降级同步处理。
 */
@Slf4j
@Configuration
public class RocketMqConfig {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.producer-group:japy-framework-producer}")
    private String producerGroup;

    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer mqProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(3000);
        try {
            producer.start();
            log.info("RocketMQ Producer 启动成功: {}", nameServer);
        } catch (Exception e) {
            log.warn("RocketMQ Producer 启动失败（操作日志将降级为同步落库）: {}", e.getMessage());
        }
        return producer;
    }
}
