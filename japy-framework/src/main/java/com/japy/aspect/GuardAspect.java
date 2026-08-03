package com.japy.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.common.BusinessException;
import com.japy.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 防护切面：
 * - @RateLimit：Redisson 令牌桶限流（用户+接口维度），超限返回 429 业务提示
 * - @Idempotent：Redis SETNX 幂等（用户+方法+参数哈希），窗口内重复请求被拒
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class GuardAspect {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Around("@annotation(rateLimit)")
    public Object limit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = "rate:" + currentUserKey() + ":" + (rateLimit.key().isBlank()
                ? pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName()
                : rateLimit.key());
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.trySetRate(RateType.PER_CLIENT, (int) rateLimit.permitsPerSecond(), 1, RateIntervalUnit.SECONDS);
        // 限流器 TTL：2 分钟无访问自动回收，避免 Redis key 泄漏
        limiter.expire(Duration.ofMinutes(2));
        if (!limiter.tryAcquire(1, 100, TimeUnit.MILLISECONDS)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
        return pjp.proceed();
    }

    @Around("@annotation(idempotent)")
    public Object idempotent(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String key = "idem:" + currentUserKey() + ":" + pjp.getSignature().getName() + ":" + hashArgs(pjp.getArgs());
        Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(idempotent.expireSeconds()));
        if (first == null || !first) {
            throw new BusinessException("请勿重复提交");
        }
        return pjp.proceed();
    }

    private String currentUserKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            return "u" + lu.getUserId();
        }
        // 匿名按 IP 维度限流（防单 IP 刷接口）
        String ip = "";
        try {
            var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                ip = sra.getRequest().getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "ip" + ip;
    }

    private String hashArgs(Object[] args) {
        try {
            String json = objectMapper.writeValueAsString(args);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(args.length);
        }
    }
}
