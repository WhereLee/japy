package com.japy.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Redis Stream 备通道（L1 降级）：RocketMQ 不可用时消息缓冲到 Redis Stream。
 * 要求 Redis ≥ 5.0（Stream 特性）；消息体：{tag, payload} 字段。
 * 持久（AOF）、不丢（Redis 存活即安全）、积压等待 MQ 恢复后由 LogDeliveryService 重放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamSender implements LogMessageSender {

    private final StringRedisTemplate redisTemplate;

    @Value("${mq.fallback.stream-key:japy:log:fallback-stream}")
    private String streamKey;

    @Override
    public boolean send(String topic, String tag, String body) {
        try {
            redisTemplate.opsForStream().add(
                    StreamRecords.string(Map.of("tag", tag, "payload", body)).withStreamKey(streamKey));
            return true;
        } catch (Exception e) {
            log.warn("Redis Stream 发送失败 stream={}: {}", streamKey, e.getMessage());
            return false;
        }
    }
}
