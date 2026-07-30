package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import com.recloud.entity.Annotation;
import com.recloud.entity.Comment;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.mapper.CommentMapper;
import com.recloud.config.BusinessMetrics;
import com.recloud.vo.AdminCommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final AnnotationMapper annotationMapper;
    private final StringRedisTemplate redisTemplate;
    private final BusinessMetrics businessMetrics;
    private final NotificationService notificationService;

    private static final String ANNOTATION_CACHE_PREFIX = "annotation:chapter:";

    @Transactional(rollbackFor = Exception.class)
    public Comment create(Long annotationId, Long userId, Long replyToId, String content) {
        // 重复内容检测：同一用户对同一批注30秒内不能发相同内容
        Long duplicateCount = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getAnnotationId, annotationId)
                        .eq(Comment::getUserId, userId)
                        .eq(Comment::getContent, content)
                        .ge(Comment::getCreatedAt, java.time.LocalDateTime.now().minusSeconds(30))
        );
        if (duplicateCount > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请勿重复提交相同评论");
        }

        Comment comment = new Comment();
        comment.setAnnotationId(annotationId);
        comment.setUserId(userId);
        comment.setReplyToId(replyToId);
        comment.setContent(content);
        commentMapper.insert(comment);
        // 原子增加评论数
        annotationMapper.updateCommentCount(annotationId, 1);
        // 清除批注缓存（commentCount 是缓存对象的一部分，必须失效）
        evictAnnotationCache(annotationId);
        businessMetrics.incrementCommentCreated();

        // 发送互动通知（失败不影响主流程）
        trySendCommentNotifications(annotationId, userId, replyToId, content);

        return comment;
    }

    public List<Comment> listByAnnotation(Long annotationId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getAnnotationId, annotationId)
                        .orderByAsc(Comment::getCreatedAt)
        );
    }

    public List<Comment> listByAnnotation(Long annotationId, int page, int size) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getAnnotationId, annotationId)
                        .orderByAsc(Comment::getCreatedAt)
                        .last("LIMIT " + size + " OFFSET " + (page - 1) * size)
        );
    }

    /**
     * 管理员分页查询评论（含评论者昵称、所评批注原文）
     * <p>
     * 走 XML 联表查询，一次 SQL 完成多表关联，避免 N+1。
     */
    public IPage<AdminCommentVO> listAdminComments(int page, int size, String keyword) {
        Page<AdminCommentVO> pageParam = new Page<>(page, size);
        return commentMapper.selectAdminCommentPage(pageParam, keyword);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteComment(Long commentId, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) return false;

        int rows = commentMapper.delete(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getId, commentId)
                        .eq(Comment::getUserId, userId)
        );
        if (rows > 0) {
            long childCount = commentMapper.selectCount(
                    new LambdaQueryWrapper<Comment>().eq(Comment::getReplyToId, commentId));
            if (childCount > 0) {
                commentMapper.delete(
                        new LambdaQueryWrapper<Comment>().eq(Comment::getReplyToId, commentId));
            }
            annotationMapper.updateCommentCount(comment.getAnnotationId(), -(1 + (int) childCount));
            evictAnnotationCache(comment.getAnnotationId());
        }
        return rows > 0;
    }

    /**
     * 管理员删除评论（级联删除子回复）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean adminDeleteComment(Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) return false;

        long childCount = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getReplyToId, commentId));

        int rows = commentMapper.deleteById(commentId);
        if (rows > 0) {
            if (childCount > 0) {
                commentMapper.delete(
                        new LambdaQueryWrapper<Comment>().eq(Comment::getReplyToId, commentId));
            }
            annotationMapper.updateCommentCount(comment.getAnnotationId(), -(1 + (int) childCount));
            evictAnnotationCache(comment.getAnnotationId());
        }
        return rows > 0;
    }

    /**
     * 发送评论/回复互动通知
     * <p>
     * 通知策略：
     * 1. 评论批注 → 通知批注作者
     * 2. 回复评论 → 同时通知批注作者 + 被回复的评论作者
     */
    private void trySendCommentNotifications(Long annotationId, Long commenterId,
                                              Long replyToId, String content) {
        try {
            // 通知批注作者
            Annotation annotation = annotationMapper.selectById(annotationId);
            if (annotation != null) {
                notificationService.sendCommentNotification(
                        annotation.getUserId(), commenterId, annotationId, content);
            }

            // 如果是回复，额外通知被回复的评论作者
            if (replyToId != null) {
                Comment replyToComment = commentMapper.selectById(replyToId);
                if (replyToComment != null) {
                    notificationService.sendReplyNotification(
                            replyToComment.getUserId(), commenterId, annotationId, content);
                }
            }
        } catch (Exception e) {
            log.warn("发送评论通知失败: annotationId={}, error={}", annotationId, e.getMessage());
        }
    }

    /**
     * 清除批注缓存（通过章节ID）
     */
    private void evictAnnotationCache(Long annotationId) {
        try {
            var annotation = annotationMapper.selectById(annotationId);
            if (annotation != null) {
                redisTemplate.delete(ANNOTATION_CACHE_PREFIX + annotation.getChapterId());
            }
        } catch (Exception e) {
            log.warn("清除批注缓存失败: {}", e.getMessage());
        }
    }
}
