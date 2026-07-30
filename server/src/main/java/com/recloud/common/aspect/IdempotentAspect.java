package com.recloud.common.aspect;

import com.recloud.common.annotation.Idempotent;
import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import com.recloud.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性切面（SpEL 表达式驱动）
 * <p>
 * 基于 Redis SETNX 实现接口幂等：
 * - key 通过 SpEL 表达式动态构建，灵活组合方法参数
 * - SETNX + EXPIRE 原子操作（SET key value NX EX ttl）
 * - TTL 窗口内相同 key 只放行一次
 * - Redis 异常降级策略：直接报错（不放行）
 *   幂等是为了防重复提交，Redis 挂了放行 = 可能重复提交 = 数据不一致
 *   与限流的区别：限流读操作可以放行，幂等必须严格
 * <p>
 * 对比旧方案（前端 Token）：
 * - 旧方案：需要前端生成 UUID 放 Header，增加前后端耦合
 * - 新方案：纯后端基于参数构建 key，前端无感知
 * <p>
 * SpEL 解析过程：
 * 1. 通过 ParameterNameDiscoverer 获取方法参数名
 * 2. 将参数名和值注入 SpEL 上下文
 * 3. 解析表达式得到 key 字符串
 * 4. 拼接前缀：idempotent:{userId}:{解析后的key}
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "idempotent:";

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.recloud.common.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        // 1. 解析 SpEL 表达式，构建幂等 key
        String parsedKey = parseKey(method, joinPoint.getArgs(), idempotent.key());

        // 2. 拼接完整 key（含 userId 隔离不同用户）
        Long userId = 0L;
        try {
            userId = SecurityUtils.getCurrentUserId();
        } catch (Exception ignored) {
        }
        String fullKey = PREFIX + userId + ":" + parsedKey;

        // 3. SETNX 原子操作（SET key value NX EX ttl）
        try {
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(fullKey, "1", idempotent.ttl(), TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(success)) {
                log.warn("幂等拦截: key={}, ttl={}s", fullKey, idempotent.ttl());
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), idempotent.message());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // Redis 异常：直接报错，不放行
            // 幂等性是数据安全保障，Redis 挂了放行 = 可能重复提交
            log.error("幂等性检查 Redis 异常，拒绝请求: {}", e.getMessage());
            throw new BizException(ResultCode.IDEMPOTENT_UNAVAILABLE.getCode(),
                    ResultCode.IDEMPOTENT_UNAVAILABLE.getMsg());
        }

        return joinPoint.proceed();
    }

    /**
     * 解析 SpEL 表达式，将方法参数注入上下文
     * <p>
     * 示例：
     * - 方法签名：create(Long chapterId, Long userId, String content)
     * - SpEL：#chapterId + ':' + #userId + ':' + #content
     * - 解析结果：123:456:这是一条批注
     */
    private String parseKey(Method method, Object[] args, String spel) {
        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
        if (paramNames == null || paramNames.length == 0) {
            return spel;
        }

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            ((StandardEvaluationContext) context).setVariable(paramNames[i], args[i]);
        }

        Expression expression = PARSER.parseExpression(spel);
        Object value = expression.getValue(context);
        return value != null ? value.toString() : "";
    }
}
