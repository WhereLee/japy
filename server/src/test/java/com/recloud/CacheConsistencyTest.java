package com.recloud;

import com.recloud.entity.Annotation;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.service.CacheConsistencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CacheConsistencyService 单元测试
 * <p>
 * 测试覆盖：
 * 1. 缓存一致 → 不触发修复
 * 2. 缓存不一致 → 自动修复（以 Redis 为准）
 * 3. dirty set 优先校验
 * 4. 大差异 → 触发告警（冷却机制）
 */
@ExtendWith(MockitoExtension.class)
class CacheConsistencyTest {

    @Mock
    private AnnotationMapper annotationMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CacheConsistencyService cacheConsistencyService;

    /**
     * 辅助方法：构建测试用 Annotation
     */
    private Annotation buildAnnotation(Long id, int likeCount) {
        Annotation annotation = new Annotation();
        annotation.setId(id);
        annotation.setLikeCount(likeCount);
        annotation.setCreatedAt(LocalDateTime.now().minusDays(1));
        return annotation;
    }

    /**
     * 辅助方法：mock 基础 Redis 操作
     */
    private void setupRedisMocks() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("缓存一致：Redis SCARD = DB like_count → 不修复")
    void testConsistent_noFix() {
        setupRedisMocks();
        // dirty set 为空
        when(setOperations.members("like:dirty")).thenReturn(Collections.emptySet());
        // 随机抽检返回 1 条批注
        when(annotationMapper.selectList(any())).thenReturn(List.of(buildAnnotation(1L, 5)));
        // Redis SCARD = 5，DB likeCount = 5 → 一致
        when(setOperations.size("like:status:1")).thenReturn(5L);
        when(annotationMapper.selectById(1L)).thenReturn(buildAnnotation(1L, 5));

        cacheConsistencyService.verifyCacheConsistency();

        // 不应触发修复
        verify(annotationMapper, never()).updateLikeCountDirect(anyLong(), anyInt());
    }

    @Test
    @DisplayName("缓存不一致：Redis=10, DB=5 → 自动修复为 10")
    void testInconsistent_autoFix() {
        setupRedisMocks();
        when(setOperations.members("like:dirty")).thenReturn(Collections.emptySet());
        when(annotationMapper.selectList(any())).thenReturn(List.of(buildAnnotation(1L, 5)));
        // Redis SCARD = 10，DB likeCount = 5 → 不一致
        when(setOperations.size("like:status:1")).thenReturn(10L);
        when(annotationMapper.selectById(1L)).thenReturn(buildAnnotation(1L, 5));
        // diff=5 >= ALERT_THRESHOLD=5，触发告警冷却检查
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        cacheConsistencyService.verifyCacheConsistency();

        // 应以 Redis 为准修复 DB
        verify(annotationMapper).updateLikeCountDirect(1L, 10);
    }

    @Test
    @DisplayName("dirty set 有数据 → 优先校验 dirty 中的批注")
    void testDirtySet_checked() {
        setupRedisMocks();
        // dirty set 包含批注 ID "1"
        when(setOperations.members("like:dirty")).thenReturn(Set.of("1"));
        // Redis SCARD = 3，DB likeCount = 3 → 一致
        when(setOperations.size("like:status:1")).thenReturn(3L);
        when(annotationMapper.selectById(1L)).thenReturn(buildAnnotation(1L, 3));
        // 随机抽检为空
        when(annotationMapper.selectList(any())).thenReturn(Collections.emptyList());

        cacheConsistencyService.verifyCacheConsistency();

        // 一致，不修复
        verify(annotationMapper, never()).updateLikeCountDirect(anyLong(), anyInt());
    }

    @Test
    @DisplayName("大差异告警冷却：同一批注 1h 内只告警一次")
    void testAlertCooldown() {
        setupRedisMocks();
        when(setOperations.members("like:dirty")).thenReturn(Collections.emptySet());
        // 两条批注都不一致且差异大
        when(annotationMapper.selectList(any())).thenReturn(
                List.of(buildAnnotation(1L, 2), buildAnnotation(2L, 3)));
        // 批注 1：Redis=20, DB=2, diff=18
        when(setOperations.size("like:status:1")).thenReturn(20L);
        when(annotationMapper.selectById(1L)).thenReturn(buildAnnotation(1L, 2));
        // 批注 2：Redis=15, DB=3, diff=12
        when(setOperations.size("like:status:2")).thenReturn(15L);
        when(annotationMapper.selectById(2L)).thenReturn(buildAnnotation(2L, 3));
        // 第一次告警成功，第二次被冷却
        when(valueOperations.setIfAbsent(contains("cache:alert:cooldown:"), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true)
                .thenReturn(false);

        cacheConsistencyService.verifyCacheConsistency();

        // 两条都应修复
        verify(annotationMapper).updateLikeCountDirect(1L, 20);
        verify(annotationMapper).updateLikeCountDirect(2L, 15);
    }
}
