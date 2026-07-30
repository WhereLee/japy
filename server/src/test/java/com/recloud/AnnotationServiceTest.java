package com.recloud;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recloud.common.lock.RedisDistributedLock;
import com.recloud.common.lock.RedisLock;
import com.recloud.config.BusinessMetrics;
import com.recloud.entity.Annotation;
import com.recloud.entity.Chapter;
import com.recloud.mapper.AnnotationLikeMapper;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.mapper.ChapterMapper;
import com.recloud.mapper.CommentMapper;
import com.recloud.service.AnnotationService;
import com.recloud.strategy.AnnotationTypeHandler;
import com.recloud.strategy.AnnotationTypeHandlerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AnnotationService 单元测试
 *
 * 用 Mockito 模拟 Mapper 层，只测业务逻辑，不依赖数据库
 */
@ExtendWith(MockitoExtension.class)
class AnnotationServiceTest {

    @Mock
    private AnnotationMapper annotationMapper;

    @Mock
    private ChapterMapper chapterMapper;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private AnnotationLikeMapper likeMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AnnotationTypeHandlerFactory handlerFactory;

    @Mock
    private BusinessMetrics businessMetrics;

    @Mock
    private RedisDistributedLock distributedLock;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AnnotationService annotationService;

    @Test
    void testCreateAnnotation() {
        // 章节存在
        when(chapterMapper.selectById(1L)).thenReturn(new Chapter());
        // 策略处理器（validate/afterCreate 为 void，默认不执行）
        AnnotationTypeHandler handler = mock(AnnotationTypeHandler.class);
        when(handlerFactory.getHandler(0)).thenReturn(handler);
        // 无重复批注
        when(annotationMapper.selectCount(any())).thenReturn(0L);
        // 模拟 Mapper 插入并回填自增 ID
        when(annotationMapper.insert(any(Annotation.class))).thenAnswer(invocation -> {
            Annotation ann = invocation.getArgument(0);
            ann.setId(1L);
            return 1;
        });

        Annotation result = annotationService.create(
                1L, 1L, 10, 20, "测试选文", "测试批注内容", 0
        );

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(1L, result.getChapterId());
        assertEquals(1L, result.getUserId());
        assertEquals(Integer.valueOf(10), result.getAnchorStart());
        assertEquals(Integer.valueOf(20), result.getAnchorEnd());
        assertEquals("测试选文", result.getSelectedText());
        assertEquals("测试批注内容", result.getContent());
        verify(annotationMapper).insert(any(Annotation.class));
    }

    @Test
    void testDeleteAnnotation() {
        Annotation existing = new Annotation();
        existing.setId(1L);
        existing.setChapterId(1L);
        existing.setUserId(1L);
        when(annotationMapper.selectById(1L)).thenReturn(existing);
        when(annotationMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        boolean result = annotationService.deleteAnnotation(1L, 1L);

        assertTrue(result);
        verify(annotationMapper).selectById(1L);
        verify(annotationMapper).delete(any(LambdaQueryWrapper.class));
        // 级联删除评论与点赞
        verify(commentMapper).delete(any(LambdaQueryWrapper.class));
        verify(likeMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void testDeleteAnnotationNotFound() {
        when(annotationMapper.selectById(999L)).thenReturn(null);

        boolean result = annotationService.deleteAnnotation(999L, 1L);

        assertFalse(result);
    }

    @Test
    void testDeleteAnnotationWrongUser() {
        Annotation existing = new Annotation();
        existing.setId(1L);
        existing.setChapterId(1L);
        existing.setUserId(1L);
        when(annotationMapper.selectById(1L)).thenReturn(existing);
        when(annotationMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

        boolean result = annotationService.deleteAnnotation(1L, 999L);

        assertFalse(result);
    }

    @Test
    void testListByChapterFromCache() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("annotation:chapter:1")).thenReturn("[{\"id\":1}]");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Collections.singletonList(new Annotation()));

        var result = annotationService.listByChapter(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(annotationMapper, never()).selectList(any());
    }

    @Test
    void testListByChapterFromDb() throws Exception {
        // 缓存未命中
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("annotation:chapter:1")).thenReturn(null);
        // 获得分布式锁
        RedisLock lock = mock(RedisLock.class);
        when(distributedLock.tryLock(anyString(), anyLong())).thenReturn(lock);
        // DB 查询
        Annotation ann = new Annotation();
        ann.setId(1L);
        when(annotationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(ann));
        // 回填缓存序列化
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"id\":1}]");

        var result = annotationService.listByChapter(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(annotationMapper).selectList(any(LambdaQueryWrapper.class));
        verify(distributedLock).unlock(lock);
    }
}
