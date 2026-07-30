package com.recloud.common.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Redis 分布式锁（Lua 脚本 + 看门狗续期）
 * <p>
 * 设计要点：
 * 1. 加锁：SET key value NX EX（value = UUID，保证只释放自己的锁）
 * 2. 释放：Lua 脚本原子操作（先 GET 比对 value，再 DEL）
 * 3. 看门狗：业务没执行完时，每 10s 自动续期（锁 TTL 30s）
 * <p>
 * 对比简单 setIfAbsent 的区别：
 * - 简单方案：锁过期但业务没完 → 其他线程拿到锁 → 数据不一致
 * - 本方案：看门狗自动续期 → 业务完成后才释放 → 安全
 * <p>
 * 降级策略：
 * Redis 不可用时，降级到本地 ReentrantLock（单机防护）。
 * 本地锁不能防分布式并发，但对于缓存防击穿场景足够：
 * 最多穿透几次 DB，不会导致数据不一致。
 * <p>
 * 使用方式：
 * <pre>
 * RedisLock lock = redisDistributedLock.tryLock("myKey");
 * if (lock != null) {
 *     try {
 *         // 业务逻辑
 *     } finally {
 *         redisDistributedLock.unlock(lock);
 *     }
 * }
 * </pre>
 */
@Slf4j
@Component
public class RedisDistributedLock {

    private final StringRedisTemplate redisTemplate;
    private final TaskScheduler watchdogScheduler;

    /** 当前节点持有的所有锁（key → RedisLock），用于停机时统一释放 */
    private final ConcurrentHashMap<String, RedisLock> heldLocks = new ConcurrentHashMap<>();
    /** 看门狗定时任务（key → ScheduledFuture），用于停止续期 */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> watchdogFutures = new ConcurrentHashMap<>();
    /** 本地锁降级：Redis 不可用时使用的本地锁（key → ReentrantLock） */
    private final ConcurrentHashMap<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    /**
     * Lua 脚本：原子释放锁
     * 只有 value 匹配时才删除（防止误删别人的锁）
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then\n" +
            "    return redis.call('del', KEYS[1])\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    /**
     * Lua 脚本：原子续期（只有 value 匹配时才续期）
     */
    private static final String RENEW_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then\n" +
            "    return redis.call('expire', KEYS[1], ARGV[2])\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    private static final DefaultRedisScript<Long> UNLOCK_REDIS_SCRIPT;
    private static final DefaultRedisScript<Long> RENEW_REDIS_SCRIPT;

    static {
        UNLOCK_REDIS_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_REDIS_SCRIPT.setScriptText(UNLOCK_SCRIPT);
        UNLOCK_REDIS_SCRIPT.setResultType(Long.class);

        RENEW_REDIS_SCRIPT = new DefaultRedisScript<>();
        RENEW_REDIS_SCRIPT.setScriptText(RENEW_SCRIPT);
        RENEW_REDIS_SCRIPT.setResultType(Long.class);
    }

    /** 看门狗续期间隔（毫秒）：锁 TTL 的 1/3 */
    private static final long WATCHDOG_INTERVAL_MS = 10_000;
    /** 默认锁 TTL（秒） */
    private static final long DEFAULT_LOCK_TTL_SECONDS = 30;

    public RedisDistributedLock(StringRedisTemplate redisTemplate,
                                @Qualifier("lockWatchdogScheduler") TaskScheduler watchdogScheduler) {
        this.redisTemplate = redisTemplate;
        this.watchdogScheduler = watchdogScheduler;
    }

    /**
     * 尝试获取分布式锁（默认 TTL 30s）
     *
     * @param key 锁的 key
     * @return RedisLock 对象（非 null 表示获取成功），null 表示获取失败
     */
    public RedisLock tryLock(String key) {
        return tryLock(key, DEFAULT_LOCK_TTL_SECONDS);
    }

    /**
     * 尝试获取分布式锁（指定 TTL）
     *
     * @param key        锁的 key
     * @param ttlSeconds 锁的过期时间（秒）
     * @return RedisLock 对象（非 null 表示获取成功），null 表示获取失败
     */
    public RedisLock tryLock(String key, long ttlSeconds) {
        String value = UUID.randomUUID().toString();

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS);

            if (!Boolean.TRUE.equals(acquired)) {
                log.debug("获取分布式锁失败（已被占用）: key={}", key);
                return null;
            }

            // 加锁成功，创建锁对象并启动看门狗
            RedisLock lock = new RedisLock(key, value, ttlSeconds);
            heldLocks.put(key, lock);
            startWatchdog(lock);

            log.debug("获取分布式锁成功: key={}", key);
            return lock;

        } catch (Exception e) {
            // Redis 不可用，降级到本地锁
            log.warn("Redis 分布式锁获取异常，降级到本地锁: key={}, error={}", key, e.getMessage());
            return tryLocalLock(key);
        }
    }

    /**
     * 降级到本地 ReentrantLock（单机防护）
     * <p>
     * 本地锁不能防分布式并发，但对于缓存防击穿场景足够：
     * 最多穿透几次 DB，不会导致数据不一致。
     * <p>
     * 返回一个“伪 RedisLock”对象，标记为本地锁，unlock 时走本地释放。
     */
    private RedisLock tryLocalLock(String key) {
        ReentrantLock localLock = localLocks.computeIfAbsent(key, k -> new ReentrantLock());
        boolean acquired = localLock.tryLock();
        if (!acquired) {
            log.debug("本地锁获取失败（已被占用）: key={}", key);
            return null;
        }
        // 创建一个标记为“本地锁”的 RedisLock（value 带 LOCAL: 前缀）
        RedisLock lock = new RedisLock(key, "LOCAL:" + UUID.randomUUID(), 0);
        heldLocks.put(key, lock);
        log.debug("本地锁获取成功（降级模式）: key={}", key);
        return lock;
    }

    /**
     * 释放分布式锁（Lua 脚本原子操作）
     * <p>
     * 只有持锁者才能释放，避免误删别人的锁。
     */
    public void unlock(RedisLock lock) {
        if (lock == null) return;

        // 判断是否为本地降级锁
        if (lock.getValue() != null && lock.getValue().startsWith("LOCAL:")) {
            unlockLocal(lock);
            return;
        }

        try {
            // 1. 停止看门狗续期
            stopWatchdog(lock.getKey());

            // 2. Lua 脚本原子释放（GET + DEL 原子执行）
            Long result = redisTemplate.execute(
                    UNLOCK_REDIS_SCRIPT,
                    Collections.singletonList(lock.getKey()),
                    lock.getValue()
            );

            if (result != null && result > 0) {
                log.debug("释放分布式锁成功: key={}", lock.getKey());
            } else {
                log.warn("释放分布式锁失败（锁可能已过期或被他人持有）: key={}", lock.getKey());
            }
        } catch (Exception e) {
            log.warn("Redis 分布式锁释放异常: key={}, error={}", lock.getKey(), e.getMessage());
        } finally {
            heldLocks.remove(lock.getKey());
        }
    }

    /**
     * 释放本地降级锁
     */
    private void unlockLocal(RedisLock lock) {
        try {
            ReentrantLock localLock = localLocks.get(lock.getKey());
            if (localLock != null && localLock.isHeldByCurrentThread()) {
                localLock.unlock();
                log.debug("释放本地锁成功: key={}", lock.getKey());
            }
        } catch (Exception e) {
            log.warn("释放本地锁异常: key={}, error={}", lock.getKey(), e.getMessage());
        } finally {
            heldLocks.remove(lock.getKey());
        }
    }

    /**
     * 释放当前节点持有的所有锁（优雅停机时调用）
     */
    public void releaseAllLocks() {
        log.info("开始释放所有分布式锁，当前持有锁数量: {}", heldLocks.size());
        heldLocks.values().forEach(this::unlock);
    }

    // ==================== 看门狗机制 ====================

    /**
     * 启动看门狗：每 10s 续期一次
     * <p>
     * 为什么需要看门狗？
     * - 锁 TTL 30s，但业务可能执行超过 30s
     * - 没有看门狗：锁过期 → 其他线程拿到锁 → 两个线程同时执行 → 数据不一致
     * - 有看门狗：每 10s 续期 → 业务完成前锁不会过期 → 安全
     */
    private void startWatchdog(RedisLock lock) {
        ScheduledFuture<?> future = watchdogScheduler.scheduleAtFixedRate(() -> {
            try {
                Long result = redisTemplate.execute(
                        RENEW_REDIS_SCRIPT,
                        Collections.singletonList(lock.getKey()),
                        lock.getValue(),
                        String.valueOf(lock.getTtlSeconds())
                );
                if (result != null && result > 0) {
                    log.debug("看门狗续期成功: key={}", lock.getKey());
                } else {
                    // 锁已不属于当前线程（可能被手动释放或异常），停止续期
                    log.warn("看门狗续期失败（锁已丢失）: key={}", lock.getKey());
                    stopWatchdog(lock.getKey());
                }
            } catch (Exception e) {
                log.warn("看门狗续期异常: key={}, error={}", lock.getKey(), e.getMessage());
            }
        }, WATCHDOG_INTERVAL_MS);

        watchdogFutures.put(lock.getKey(), future);
    }

    /**
     * 停止看门狗
     */
    private void stopWatchdog(String key) {
        ScheduledFuture<?> future = watchdogFutures.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }
}
