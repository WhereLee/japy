package com.recloud;

import com.recloud.common.aspect.IdempotentAspect;
import com.recloud.common.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotentAspect 单元测试
 * <p>
 * 测试覆盖：
 * 1. 首次请求 → SETNX 成功 → 放行
 * 2. 重复请求 → SETNX 返回 false → 拦截
 * 3. Redis 异常 → 直接报错（不放行，保障数据一致性）
 * 4. SpEL 表达式解析正确性
 */
@ExtendWith(MockitoExtension.class)
class IdempotentAspectTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private IdempotentAspect idempotentAspect;

    /**
     * 测试辅助方法：获取带 @Idempotent 注解的测试方法
     */
    private Method getTestAnnotatedMethod() throws NoSuchMethodException {
        return IdempotentAspectTest.class
                .getDeclaredMethod("annotatedTestMethod", Long.class, Long.class, String.class);
    }

    /**
     * 带 @Idempotent 注解的测试方法（用于反射获取注解）
     */
    @com.recloud.common.annotation.Idempotent(
            key = "#chapterId + ':' + #userId + ':' + #content",
            ttl = 30,
            message = "请勿重复提交"
    )
    public void annotatedTestMethod(Long chapterId, Long userId, String content) {
        // 测试用方法
    }

    private void setupJoinPoint() throws Exception {
        Method method = getTestAnnotatedMethod();
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, 100L, "测试内容"});
    }

    @Test
    @DisplayName("首次请求：SETNX 返回 true → 放行，执行业务方法")
    void testFirstRequest_allowed() throws Throwable {
        setupJoinPoint();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(joinPoint.proceed()).thenReturn("success");

        Object result = idempotentAspect.around(joinPoint);

        assertEquals("success", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("重复请求：SETNX 返回 false → 抛出 BizException")
    void testDuplicateRequest_blocked() throws Throwable {
        setupJoinPoint();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> idempotentAspect.around(joinPoint));

        assertEquals("请勿重复提交", exception.getMessage());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("Redis 异常：直接报错，不放行（保障数据一致性）")
    void testRedisDown_rejectRequest() throws Throwable {
        setupJoinPoint();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(30L), eq(TimeUnit.SECONDS)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        BizException exception = assertThrows(BizException.class,
                () -> idempotentAspect.around(joinPoint));

        // 应使用 IDEMPOTENT_UNAVAILABLE 错误码（5003）
        assertEquals(5003, exception.getCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("SpEL 解析：key 应包含参数值拼接")
    void testSpEL_keyParsing() throws Throwable {
        setupJoinPoint();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        idempotentAspect.around(joinPoint);

        // 验证 key 包含 SpEL 解析后的参数：idempotent:{userId}:1:100:测试内容
        verify(valueOperations).setIfAbsent(
                argThat(key -> key.contains("idempotent:") && key.contains("1:100:测试内容")),
                eq("1"), eq(30L), eq(TimeUnit.SECONDS));
    }
}
