package com.japy.mq;

/**
 * 日志消息发送通道抽象：多级降级的统一入口。
 * 主通道 RocketMQ（RocketMqSender），备通道 Redis Stream（RedisStreamSender）。
 * 返回 false 表示当前通道不可用，由上层（LogDeliveryService）决定降级路径。
 */
public interface LogMessageSender {

    /**
     * 发送一条日志消息。
     *
     * @param topic 逻辑主题（MQ 用；Stream 通道忽略，使用固定 stream key）
     * @param tag   消息标签（MQ 用；Stream 通道写入消息体）
     * @param body  消息体（操作日志 JSON）
     * @return true=发送成功；false=通道不可用/发送失败
     */
    boolean send(String topic, String tag, String body);
}
