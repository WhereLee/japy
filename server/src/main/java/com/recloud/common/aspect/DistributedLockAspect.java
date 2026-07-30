package com.recloud.common.aspect;

import com.recloud.common.annotation.DistributedLock;
import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import com.recloud.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 分布式锁切面
 * <p>
 * 基于 Redis SETNX 实现分布式锁：
 * - Key = lock:{prefix}:{userId}:{method}:{args}
 * - Value = UUID（锁持有者标识）
 * - SETNX + EXPIRE 原子操作
 * - 释放时用 Lua 脚本原子校验并删除（防止误删别人的锁）
 * - Redis 异常降级放行
 * <p>
 * 注意：这是非重入锁，同一线程不可重复获取。
 * 适用于短时间防并发场景（如点赞双击、表单重复提交）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "lock:";

    /**
     * Lua 脚本：原子校验并删除锁
     * 只有锁的持有者才能释放锁
     */
    private static final String UNLOCK_LUA =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('DEL', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    /** 缓存的 Lua 脚本实例（避免每次释放锁都创建新对象） */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(UNLOCK_LUA);
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Around("@annotation(com.recloud.common.annotation.DistributedLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock lock = method.getAnnotation(DistributedLock.class);

        // 构建锁 Key
        String lockKey = buildLockKey(lock, method, joinPoint.getArgs());
        // 生成唯一锁标识（UUID）
        String lockValue = UUID.randomUUID().toString();

        // 尝试获取锁
        Boolean acquired = false;
        try {
            acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, lock.expire(), TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis 异常降级放行
            log.warn("分布式锁获取异常，降级放行: {}", e.getMessage());
            return joinPoint.proceed();
        }

        if (!Boolean.TRUE.equals(acquired)) {
            log.warn("分布式锁获取失败: key={}", lockKey);
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), lock.message());
        }

        try {
            return joinPoint.proceed();
        } finally {
            // 释放锁（Lua 脚本原子校验并删除，使用缓存的脚本实例）
            try {
                Long result = redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
                if (result == null || result == 0L) {
                    log.warn("分布式锁释放失败（锁已过期或不属于当前线程）: key={}", lockKey);
                }
            } catch (Exception e) {
                log.warn("分布式锁释放异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 构建锁 Key：lock:{prefix}:{userId}:{method}:{args}
     */
    private String buildLockKey(DistributedLock lock, Method method, Object[] args) {
        StringBuilder sb = new StringBuilder(PREFIX);

        // 前缀
        if (!lock.prefix().isEmpty()) {
            sb.append(lock.prefix()).append(":");
        }

        // userId
        try {
            sb.append(SecurityUtils.getCurrentUserId()).append(":");
        } catch (Exception e) {
            sb.append("anonymous:");
        }

        // 方法名
        sb.append(method.getDeclaringClass().getSimpleName())
          .append(".")
          .append(method.getName());

        // 参数（取前3个参数的 hashCode）
        if (args != null && args.length > 0) {
            String argsStr = Arrays.stream(args)
                    .limit(3)
                    .map(arg -> arg != null ? String.valueOf(arg.hashCode()) : "null")
                    .collect(Collectors.joining("_"));
            sb.append(":").append(argsStr);
        }

        return sb.toString();
    }
}
