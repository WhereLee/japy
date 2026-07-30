package com.recloud;

import com.recloud.config.BusinessMetrics;
import com.recloud.entity.Annotation;
import com.recloud.entity.AnnotationLike;
import com.recloud.mapper.AnnotationLikeMapper;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.service.AnnotationLikeService;
import com.recloud.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AnnotationLikeService 单元测试
 * <p>
 * 测试覆盖：
 * 1. Redis 点赞 toggle（SADD/SREM）
 * 2. Redis 不可用时降级到 DB
 * 3. 批量查询点赞状态（Redis + DB 降级）
 * 4. 点赞计数查询（SCARD + DB 降级）
 */
@ExtendWith(MockitoExtension.class)
class AnnotationLikeServiceTest {

    @Mock
    private AnnotationLikeMapper likeMapper;

    @Mock
    private AnnotationMapper annotationMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private BusinessMetrics businessMetrics;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AnnotationLikeService likeService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ========== toggle 测试 ==========

    @Test
    @DisplayName("Redis 点赞：SADD 返回 1 → 点赞成功，返回 liked=true")
    void testToggle_redisLike_success() {
        // 初始化检查通过
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        // SADD 返回 1 = 新增点赞
        when(setOperations.add(eq("like:status:1"), eq("100"))).thenReturn(1L);
        // SCARD 返回计数
        when(setOperations.size("like:status:1")).thenReturn(5L);
        // dirty 标记
        when(setOperations.add(eq("like:dirty"), eq("1"))).thenReturn(1L);
        // expire
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        Map<String, Object> result = likeService.toggle(1L, 100L);

        assertTrue((Boolean) result.get("liked"));
        assertEquals(5L, result.get("likeCount"));
        verify(setOperations).add("like:status:1", "100");
    }

    @Test
    @DisplayName("Redis 取消点赞：SADD 返回 0 → 已存在，执行 SREM 取消")
    void testToggle_redisUnlike_success() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        // SADD 返回 0 = 已存在
        when(setOperations.add(eq("like:status:1"), eq("100"))).thenReturn(0L);
        // SREM 取消点赞
        when(setOperations.remove("like:status:1", "100")).thenReturn(1L);
        // SCARD 返回取消后的计数
        when(setOperations.size("like:status:1")).thenReturn(3L);
        when(setOperations.add(eq("like:dirty"), eq("1"))).thenReturn(1L);

        Map<String, Object> result = likeService.toggle(1L, 100L);

        assertFalse((Boolean) result.get("liked"));
        assertEquals(3L, result.get("likeCount"));
        verify(setOperations).remove("like:status:1", "100");
    }

    @Test
    @DisplayName("Redis 不可用 → 降级到 DB（插入新点赞）")
    void testToggle_redisDown_fallbackToDb_insert() {
        // Redis 操作全部抛异常
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));
        // DB 降级：当前未点赞（delete 返回 0）
        when(likeMapper.delete(any())).thenReturn(0);
        when(likeMapper.insert(any(AnnotationLike.class))).thenReturn(1);
        when(annotationMapper.updateLikeCount(1L, 1)).thenReturn(1);
        when(likeMapper.selectCount(any())).thenReturn(1L);

        Map<String, Object> result = likeService.toggle(1L, 100L);

        assertTrue((Boolean) result.get("liked"));
        assertEquals(1L, result.get("likeCount"));
        verify(likeMapper).insert(any(AnnotationLike.class));
        verify(annotationMapper).updateLikeCount(1L, 1);
    }

    @Test
    @DisplayName("Redis 不可用 → 降级到 DB（取消已有点赞）")
    void testToggle_redisDown_fallbackToDb_delete() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));
        // DB 降级：当前已点赞（delete 返回 1）
        when(likeMapper.delete(any())).thenReturn(1);
        when(annotationMapper.updateLikeCount(1L, -1)).thenReturn(1);
        when(likeMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> result = likeService.toggle(1L, 100L);

        assertFalse((Boolean) result.get("liked"));
        assertEquals(0L, result.get("likeCount"));
        verify(annotationMapper).updateLikeCount(1L, -1);
    }

    // ========== isLiked 测试 ==========

    @Test
    @DisplayName("isLiked：Redis SISMEMBER 返回 true → 已点赞")
    void testIsLiked_redisTrue() {
        when(setOperations.isMember("like:status:1", "100")).thenReturn(true);

        assertTrue(likeService.isLiked(1L, 100L));
    }

    @Test
    @DisplayName("isLiked：Redis 异常 → 降级到 DB 查询")
    void testIsLiked_redisDown_fallbackToDb() {
        when(setOperations.isMember(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis down"));
        when(likeMapper.selectCount(any())).thenReturn(1L);

        assertTrue(likeService.isLiked(1L, 100L));
        verify(likeMapper).selectCount(any());
    }

    // ========== countByAnnotation 测试 ==========

    @Test
    @DisplayName("countByAnnotation：Redis SCARD 返回精确计数")
    void testCount_redis() {
        when(setOperations.size("like:status:1")).thenReturn(42L);

        assertEquals(42L, likeService.countByAnnotation(1L));
    }

    @Test
    @DisplayName("countByAnnotation：Redis 异常 → 降级到 DB COUNT")
    void testCount_redisDown_fallbackToDb() {
        when(setOperations.size(anyString()))
                .thenThrow(new RuntimeException("Redis down"));
        when(likeMapper.selectCount(any())).thenReturn(38L);

        assertEquals(38L, likeService.countByAnnotation(1L));
    }

    // ========== batchIsLiked 测试 ==========

    @Test
    @DisplayName("batchIsLiked：Redis 批量查询，返回已点赞的 ID 集合")
    void testBatchIsLiked_redis() {
        when(setOperations.isMember("like:status:1", "100")).thenReturn(true);
        when(setOperations.isMember("like:status:2", "100")).thenReturn(false);
        when(setOperations.isMember("like:status:3", "100")).thenReturn(true);

        Set<Long> result = likeService.batchIsLiked(java.util.List.of(1L, 2L, 3L), 100L);

        assertEquals(Set.of(1L, 3L), result);
    }

    @Test
    @DisplayName("batchIsLiked：Redis 异常 → 降级到 DB 批量查询")
    void testBatchIsLiked_redisDown_fallbackToDb() {
        when(setOperations.isMember(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis down"));

        AnnotationLike like1 = new AnnotationLike();
        like1.setAnnotationId(1L);
        AnnotationLike like3 = new AnnotationLike();
        like3.setAnnotationId(3L);
        when(likeMapper.selectList(any())).thenReturn(java.util.List.of(like1, like3));

        Set<Long> result = likeService.batchIsLiked(java.util.List.of(1L, 2L, 3L), 100L);

        assertEquals(Set.of(1L, 3L), result);
    }

    @Test
    @DisplayName("batchIsLiked：空列表 → 返回空集合")
    void testBatchIsLiked_emptyList() {
        Set<Long> result = likeService.batchIsLiked(java.util.Collections.emptyList(), 100L);
        assertTrue(result.isEmpty());
    }
}
