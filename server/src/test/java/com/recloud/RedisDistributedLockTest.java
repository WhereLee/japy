package com.recloud;

import com.recloud.common.lock.RedisDistributedLock;
import com.recloud.common.lock.RedisLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.TaskScheduler;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisDistributedLock 单元测试
 * <p>
 * 测试覆盖：
 * 1. 正常加锁/释放
 * 2. 加锁失败（已被占用）
 * 3. Redis 异常降级到本地锁
 * 4. 本地锁释放
 */
@ExtendWith(MockitoExtension.class)
class RedisDistributedLockTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private TaskScheduler watchdogScheduler;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisDistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("正常加锁：Redis SETNX 返回 true → 获得锁")
    void testTryLock_success() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(watchdogScheduler.scheduleAtFixedRate(any(), anyLong())).thenReturn(mock(java.util.concurrent.ScheduledFuture.class));

        RedisLock lock = distributedLock.tryLock("test:key");

        assertNotNull(lock);
        assertEquals("test:key", lock.getKey());
        verify(valueOperations).setIfAbsent(eq("test:key"), anyString(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("加锁失败：Redis SETNX 返回 false → 锁已被占用")
    void testTryLock_alreadyLocked() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        RedisLock lock = distributedLock.tryLock("test:key");

        assertNull(lock);
    }

    @Test
    @DisplayName("Redis 异常降级：Redis 挂了 → 降级到本地 ReentrantLock")
    void testTryLock_redisDown_fallbackToLocal() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        RedisLock lock = distributedLock.tryLock("test:key");

        // 本地锁应该能获取成功（第一次没有竞争）
        assertNotNull(lock);
        assertTrue(lock.getValue().startsWith("LOCAL:"));
    }

    @Test
    @DisplayName("释放锁：Lua 脚本执行正确")
    void testUnlock() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(watchdogScheduler.scheduleAtFixedRate(any(), anyLong())).thenReturn(mock(java.util.concurrent.ScheduledFuture.class));
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(1L);

        RedisLock lock = distributedLock.tryLock("test:key");
        assertNotNull(lock);

        distributedLock.unlock(lock);

        // 验证 Lua 脚本被执行
        verify(redisTemplate).execute(any(), anyList(), eq(lock.getValue()));
    }

    @Test
    @DisplayName("释放本地降级锁：不走 Lua 脚本")
    void testUnlock_localLock() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new RuntimeException("Redis down"));

        RedisLock lock = distributedLock.tryLock("test:key");
        assertNotNull(lock);
        assertTrue(lock.getValue().startsWith("LOCAL:"));

        // 释放本地锁不应调用 Redis
        distributedLock.unlock(lock);

        // Lua 脚本只在看门狗续期时可能调用，unlock 时本地锁不走 Redis
        verify(redisTemplate, never()).execute(
                argThat(script -> script != null),
                argThat(list -> list != null && list.contains("test:key")),
                anyString()
        );
    }
}
