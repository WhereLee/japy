package com.recloud;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recloud.common.exception.BizException;
import com.recloud.config.BusinessMetrics;
import com.recloud.entity.Annotation;
import com.recloud.entity.Comment;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.mapper.CommentMapper;
import com.recloud.service.CommentService;
import com.recloud.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CommentService 单元测试
 *
 * 用 Mockito 模拟 Mapper 层，只测业务逻辑，不依赖数据库
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private AnnotationMapper annotationMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private BusinessMetrics businessMetrics;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CommentService commentService;

    @Test
    void testCreateComment() {
        // 无重复评论
        when(commentMapper.selectCount(any())).thenReturn(0L);
        when(commentMapper.insert(any(Comment.class))).thenAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            c.setId(1L);
            return 1;
        });
        // 批注存在（用于清缓存 + 发通知）
        Annotation annotation = new Annotation();
        annotation.setId(100L);
        annotation.setChapterId(1L);
        annotation.setUserId(99L);
        when(annotationMapper.selectById(100L)).thenReturn(annotation);

        Comment result = commentService.create(100L, 1L, null, "评论内容");

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(100L, result.getAnnotationId());
        assertEquals(1L, result.getUserId());
        assertEquals("评论内容", result.getContent());
        // 评论数原子 +1
        verify(annotationMapper).updateCommentCount(100L, 1);
        // 通知批注作者
        verify(notificationService).sendCommentNotification(99L, 1L, 100L, "评论内容");
    }

    @Test
    void testCreateCommentDuplicate() {
        when(commentMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class,
                () -> commentService.create(100L, 1L, null, "重复内容"));
        verify(commentMapper, never()).insert(any());
    }

    @Test
    void testDeleteComment() {
        Comment comment = new Comment();
        comment.setId(1L);
        comment.setAnnotationId(100L);
        comment.setUserId(1L);
        when(commentMapper.selectById(1L)).thenReturn(comment);
        when(commentMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        // 无子回复
        when(commentMapper.selectCount(any())).thenReturn(0L);
        Annotation annotation = new Annotation();
        annotation.setId(100L);
        annotation.setChapterId(1L);
        when(annotationMapper.selectById(100L)).thenReturn(annotation);

        boolean result = commentService.deleteComment(1L, 1L);

        assertTrue(result);
        // 评论数原子 -1（无子回复）
        verify(annotationMapper).updateCommentCount(100L, -1);
    }

    @Test
    void testDeleteCommentNotFound() {
        when(commentMapper.selectById(999L)).thenReturn(null);

        assertFalse(commentService.deleteComment(999L, 1L));
    }

    @Test
    void testDeleteCommentWrongUser() {
        Comment comment = new Comment();
        comment.setId(1L);
        comment.setAnnotationId(100L);
        comment.setUserId(1L);
        when(commentMapper.selectById(1L)).thenReturn(comment);
        // userId 不匹配，删除 0 行
        when(commentMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

        assertFalse(commentService.deleteComment(1L, 999L));
    }
}
