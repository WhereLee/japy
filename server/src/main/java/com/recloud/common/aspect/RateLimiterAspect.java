package com.recloud.common.aspect;

import com.recloud.common.annotation.RateLimiter;
import com.recloud.common.annotation.RateLimiter.RateLimitAlgorithm;
import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import com.recloud.common.util.IpUtils;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 分布式限流切面（支持固定窗口 + 滑动窗口）
 * <p>
 * 固定窗口（FIXED_WINDOW）：
 * - Lua 脚本：GET + INCR + EXPIRE 原子操作
 * - 优点：简单高效，Redis 操作少
 * - 缺点：窗口边界处可能有 2 倍突发（窗口1末尾 + 窗口2开头）
 * <p>
 * 滑动窗口（SLIDING_WINDOW）：
 * - Lua 脚本：ZREMRANGEBYSCORE + ZCARD + ZADD + EXPIRE 原子操作
 * - 优点：精确控制任意时间段内的请求数，无边界突发
 * - 缺点：每次请求需要 ZADD 一条记录，Redis 内存占用稍高
 * <p>
 * 两种算法都支持 Redis 异常降级：
 * - strict=true（写操作）：Redis 挂了 → 拒绝请求
 * - strict=false（读操作）：Redis 挂了 → 放行
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimiterAspect {

    private final StringRedisTemplate redisTemplate;

    /**
     * 固定窗口 Lua 脚本
     * KEYS[1] = 限流 key
     * ARGV[1] = 限流阈值 limit
     * ARGV[2] = 时间窗口（秒）
     * 返回: 1=放行, 0=拒绝
     */
    private static final String FIXED_WINDOW_SCRIPT =
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local current = tonumber(redis.call('GET', key) or '0')\n" +
            "if current >= limit then\n" +
            "    return 0\n" +
            "else\n" +
            "    current = redis.call('INCR', key)\n" +
            "    if current == 1 then\n" +
            "        redis.call('EXPIRE', key, window)\n" +
            "    end\n" +
            "    return 1\n" +
            "end";

    /**
     * 滑动窗口 Lua 脚本（基于 Redis ZSET）
     * <p>
     * 原理：
     * - ZSET 的 score = 请求时间戳（毫秒）
     * - ZSET 的 member = 请求唯一 ID（UUID）
     * - 每次请求先清除窗口外的记录，再统计窗口内的记录数
     * <p>
     * KEYS[1] = 限流 key
     * ARGV[1] = 限流阈值 limit
     * ARGV[2] = 时间窗口（秒）
     * ARGV[3] = 当前时间戳（毫秒）
     * ARGV[4] = 窗口起始时间戳（毫秒）= now - window*1000
     * ARGV[5] = 请求唯一 ID（UUID）
     * 返回: 1=放行, 0=拒绝
     */
    private static final String SLIDING_WINDOW_SCRIPT =
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local windowStart = tonumber(ARGV[4])\n" +
            "local requestId = ARGV[5]\n" +
            // 1. 清除窗口外的记录
            "redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)\n" +
            // 2. 统计当前窗口内的请求数
            "local count = redis.call('ZCARD', key)\n" +
            // 3. 判断是否超限
            "if count >= limit then\n" +
            "    return 0\n" +
            "end\n" +
            // 4. 未超限，记录当前请求
            "redis.call('ZADD', key, now, requestId)\n" +
            "redis.call('EXPIRE', key, window)\n" +
            "return 1\n";

    private static final DefaultRedisScript<Long> FIXED_WINDOW_REDIS_SCRIPT;
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_REDIS_SCRIPT;

    static {
        FIXED_WINDOW_REDIS_SCRIPT = new DefaultRedisScript<>();
        FIXED_WINDOW_REDIS_SCRIPT.setScriptText(FIXED_WINDOW_SCRIPT);
        FIXED_WINDOW_REDIS_SCRIPT.setResultType(Long.class);

        SLIDING_WINDOW_REDIS_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_REDIS_SCRIPT.setScriptText(SLIDING_WINDOW_SCRIPT);
        SLIDING_WINDOW_REDIS_SCRIPT.setResultType(Long.class);
    }

    @Around("@annotation(com.recloud.common.annotation.RateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimiter rateLimiter = method.getAnnotation(RateLimiter.class);

        String key = buildKey(rateLimiter);

        try {
            boolean allowed;
            if (rateLimiter.algorithm() == RateLimitAlgorithm.SLIDING_WINDOW) {
                allowed = executeSlidingWindow(key, rateLimiter);
            } else {
                allowed = executeFixedWindow(key, rateLimiter);
            }

            if (!allowed) {
                log.warn("限流触发: key={}, algorithm={}, limit={}/{}s",
                        key, rateLimiter.algorithm(), rateLimiter.limit(), rateLimiter.time());
                throw new BizException(ResultCode.RATE_LIMIT);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // Redis 异常降级：根据 strict 属性决定行为
            if (rateLimiter.strict()) {
                // 严格模式（写操作）：拒绝请求，防止刷接口产生脏数据
                log.warn("限流 Redis 异常，严格模式拒绝: key={}", key);
                throw new BizException(ResultCode.SYSTEM_BUSY.getCode(), ResultCode.SYSTEM_BUSY.getMsg());
            } else {
                // 宽松模式（读操作）：放行，宁可多查几次 DB，不能拒绝用户
                log.warn("限流 Redis 异常，宽松模式降级放行: key={}", key);
            }
        }

        return joinPoint.proceed();
    }

    /**
     * 执行固定窗口限流
     */
    private boolean executeFixedWindow(String key, RateLimiter rateLimiter) {
        Long result = redisTemplate.execute(
                FIXED_WINDOW_REDIS_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(rateLimiter.limit()),
                String.valueOf(rateLimiter.time())
        );
        return result != null && result == 1;
    }

    /**
     * 执行滑动窗口限流
     */
    private boolean executeSlidingWindow(String key, RateLimiter rateLimiter) {
        long now = System.currentTimeMillis();
        long windowMs = rateLimiter.time() * 1000L;
        long windowStart = now - windowMs;
        String requestId = UUID.randomUUID().toString();

        Long result = redisTemplate.execute(
                SLIDING_WINDOW_REDIS_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(rateLimiter.limit()),
                String.valueOf(rateLimiter.time()),
                String.valueOf(now),
                String.valueOf(windowStart),
                requestId
        );
        return result != null && result == 1;
    }

    /**
     * 构建限流 key
     * <p>
     * 已登录：rate_limit:{userId}:{自定义key}
     * 未登录：rate_limit:ip:{clientIp}:{自定义key}
     */
    private String buildKey(RateLimiter rateLimiter) {
        String customKey = rateLimiter.key().isEmpty() ? "default" : rateLimiter.key();

        // 尝试获取 userId
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            return "rate_limit:" + userId + ":" + customKey;
        } catch (Exception ignored) {
            // 未登录，使用 IP
        }

        // 未登录场景用 IP
        String ip = "unknown";
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                ip = IpUtils.getClientIp(attrs.getRequest());
            }
        } catch (Exception ignored) {
        }

        return "rate_limit:ip:" + ip + ":" + customKey;
    }
}
