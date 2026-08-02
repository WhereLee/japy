package com.japy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.PageResult;
import com.japy.entity.Comment;
import com.japy.entity.Moment;
import com.japy.mapper.CommentMapper;
import com.japy.mapper.MomentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论：支持一层楼中楼。
 * 返回结构：顶层评论分页，每条附带其全部子回复（replies）。
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final MomentMapper momentMapper;
    private final NotificationService notificationService;

    public PageResult<Map<String, Object>> listByMoment(Long momentId, int page, int size, int replySize) {
        // 校验动态存在且未删除（防止删除后评论成为可查的孤儿数据）
        Moment moment = momentMapper.selectById(momentId);
        if (moment == null || moment.getStatus() == 2) {
            throw new IllegalArgumentException("动态不存在");
        }
        // 1. 顶层评论分页
        Page<Comment> p = new Page<>(page, size);
        Page<Comment> topPage = commentMapper.selectPage(p,
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getMomentId, momentId)
                        .isNull(Comment::getParentId)
                        .eq(Comment::getStatus, 0)
                        .orderByAsc(Comment::getCreatedAt)
                        .orderByAsc(Comment::getId));

        List<Map<String, Object>> list = topPage.getRecords().stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("userId", c.getUserId());
            item.put("nickname", c.getNickname());
            item.put("content", c.getContent());
            item.put("createdAt", c.getCreatedAt());
            return item;
        }).collect(Collectors.toList());

        // 2. 一次性查出这批顶层评论的子回复（一层楼中楼），按最新在前截取 replySize 条
        if (!topPage.getRecords().isEmpty()) {
            List<Long> topIds = topPage.getRecords().stream().map(Comment::getId).collect(Collectors.toList());
            List<Comment> replies = commentMapper.selectList(
                    new LambdaQueryWrapper<Comment>()
                            .in(Comment::getParentId, topIds)
                            .eq(Comment::getStatus, 0)
                            .orderByDesc(Comment::getCreatedAt)
                            .orderByDesc(Comment::getId));
            // 每条顶层评论只保留最新的 replySize 条，再按时间正序展示
            Map<Long, List<Comment>> byParent = replies.stream()
                    .collect(Collectors.groupingBy(Comment::getParentId));
            for (Map<String, Object> item : list) {
                Long topId = (Long) item.get("id");
                List<Comment> recent = byParent.getOrDefault(topId, List.of());
                if (recent.size() > replySize) {
                    recent = recent.subList(0, replySize);
                }
                // 反转回时间正序
                java.util.Collections.reverse(recent);
                item.put("replies", recent);
            }
        }

        return PageResult.of(list, topPage.getTotal(), page, size);
    }

    /**
     * 发表评论。parentId 非空时为回复（只能回复顶层评论，保证一层楼中楼）。
     */
    @Transactional
    public Comment create(Long momentId, Long userId, String nickname,
                          Long parentId, String replyTo, String content) {
        // 动态必须存在且正常
        Moment moment = momentMapper.selectById(momentId);
        if (moment == null || moment.getStatus() != 0) {
            throw new IllegalArgumentException("动态不存在");
        }

        // 回复时校验父评论：必须存在、属于同一动态、且是顶层评论
        if (parentId != null) {
            Comment parent = commentMapper.selectById(parentId);
            if (parent == null || parent.getStatus() == 2) {
                throw new IllegalArgumentException("被回复的评论不存在");
            }
            if (!parent.getMomentId().equals(momentId)) {
                throw new IllegalArgumentException("评论与动态不匹配");
            }
            if (parent.getParentId() != null) {
                throw new IllegalArgumentException("只能回复顶层评论");
            }
        }

        Comment comment = new Comment();
        comment.setMomentId(momentId);
        comment.setUserId(userId);
        comment.setNickname(nickname);
        comment.setParentId(parentId);
        comment.setReplyTo(replyTo);
        comment.setContent(content);
        comment.setStatus(0);
        commentMapper.insert(comment);

        // 计数
        momentMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Moment>()
                        .eq(Moment::getId, momentId)
                        .setSql("comment_count = comment_count + 1"));

        // 通知：回复通知被回复人；评论通知动态作者
        if (parentId != null) {
            Comment parent = commentMapper.selectById(parentId);
            if (parent.getUserId() != null && !parent.getUserId().equals(userId)) {
                notificationService.send(parent.getUserId(), "reply", "comment", comment.getId(),
                        nickname + " 回复了你的评论：" + truncate(content));
            }
        } else if (moment.getUserId() != null && !moment.getUserId().equals(userId)) {
            notificationService.send(moment.getUserId(), "comment", "moment", momentId,
                    nickname + " 评论了你的动态：" + truncate(content));
        }
        return comment;
    }

    /** 软删除（本人删除自己的评论，含其子回复） */
    @Transactional
    public boolean softDelete(Long id, Long userId) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null || comment.getStatus() == 2) return false;
        if (!comment.getUserId().equals(userId)) return false;

        // 删除自己（若为顶层评论，其子回复一并删除）
        if (comment.getParentId() == null) {
            commentMapper.update(null,
                    new LambdaUpdateWrapper<Comment>()
                            .eq(Comment::getParentId, id)
                            .set(Comment::getStatus, 2));
        }
        commentMapper.update(null,
                new LambdaUpdateWrapper<Comment>()
                        .eq(Comment::getId, id)
                        .set(Comment::getStatus, 2));

        // 递减动态评论数（重新统计该动态的可见评论数）
        Long realCount = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getMomentId, comment.getMomentId())
                        .eq(Comment::getStatus, 0));
        momentMapper.update(null,
                new LambdaUpdateWrapper<Moment>()
                        .eq(Moment::getId, comment.getMomentId())
                        .set(Moment::getCommentCount, realCount.intValue()));
        return true;
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 30 ? s.substring(0, 30) + "…" : s;
    }
}
