package com.recloud.common.lock;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 分布式锁持有信息
 * <p>
 * 记录锁的 key、value（UUID）、TTL，用于释放和续期。
 */
@Data
@AllArgsConstructor
public class RedisLock {
    /** 锁的 Redis key */
    private final String key;
    /** 锁的 value（UUID），用于释放时校验 */
    private final String value;
    /** 锁的 TTL（秒） */
    private final long ttlSeconds;
}
