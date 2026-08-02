package com.japy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.PageResult;
import com.japy.entity.Comment;
import com.japy.entity.Moment;
import com.japy.entity.MomentLike;
import com.japy.mapper.CommentMapper;
import com.japy.mapper.MomentLikeMapper;
import com.japy.mapper.MomentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MomentService {

    private final MomentMapper momentMapper;
    private final MomentLikeMapper likeMapper;
    private final CommentMapper commentMapper;
    private final NotificationService notificationService;

    /**
     * 全局时间线（置顶优先 + 最新在前），并填充当前用户的点赞状态
     */
    public PageResult<Moment> list(int page, int size, Long currentUserId) {
        Page<Moment> p = new Page<>(page, size);
        LambdaQueryWrapper<Moment> wrapper = new LambdaQueryWrapper<Moment>()
                .eq(Moment::getStatus, 0)
                .orderByDesc(Moment::getPinned)
                .orderByDesc(Moment::getCreatedAt)
                .orderByDesc(Moment::getId);
        Page<Moment> result = momentMapper.selectPage(p, wrapper);
        fillLikedState(result.getRecords(), currentUserId);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    /**
     * 游标分页（滚动加载）：基于 (created_at, id) 行值比较，翻页不重复、不遗漏。
     * cursor 格式："createdAt_id"，取自上一页最后一条。
     */
    public PageResult<Moment> listByCursor(String cursor, int size, Long currentUserId) {
        String[] parts = cursor.split("_", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("cursor格式错误");
        }
        final LocalDateTime createdAt;
        final long id;
        try {
            createdAt = LocalDateTime.parse(parts[0]);
            id = Long.parseLong(parts[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("cursor格式错误");
        }
        List<Moment> records = momentMapper.selectList(
                new LambdaQueryWrapper<Moment>()
                        .eq(Moment::getStatus, 0)
                        // 行值比较：(created_at, id) 字典序 < 游标位置
                        .apply("(created_at, id) < ({0}, {1})", createdAt, id)
                        .orderByDesc(Moment::getCreatedAt)
                        .orderByDesc(Moment::getId)
                        .last("LIMIT " + size)); // size 已由 PageParams 规整为 int
        fillLikedState(records, currentUserId);
        // 游标模式无 total 语义，返回本页条数
        return PageResult.of(records, records.size(), 1, size);
    }

    /**
     * 某用户的动态（公开主页用，仅正常状态）
     */
    public PageResult<Moment> listByUser(Long userId, int page, int size, Long currentUserId) {
        Page<Moment> p = new Page<>(page, size);
        LambdaQueryWrapper<Moment> wrapper = new LambdaQueryWrapper<Moment>()
                .eq(Moment::getUserId, userId)
                .eq(Moment::getStatus, 0)
                .orderByDesc(Moment::getCreatedAt)
                .orderByDesc(Moment::getId);
        Page<Moment> result = momentMapper.selectPage(p, wrapper);
        fillLikedState(result.getRecords(), currentUserId);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    /**
     * 我的动态（本人可见被隐藏的）
     */
    public PageResult<Moment> listMine(int page, int size, Long userId) {
        Page<Moment> p = new Page<>(page, size);
        LambdaQueryWrapper<Moment> wrapper = new LambdaQueryWrapper<Moment>()
                .eq(Moment::getUserId, userId)
                .in(Moment::getStatus, 0, 1)
                .orderByDesc(Moment::getCreatedAt)
                .orderByDesc(Moment::getId);
        Page<Moment> result = momentMapper.selectPage(p, wrapper);
        fillLikedState(result.getRecords(), userId);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public Moment create(Long userId, String nickname, String content) {
        Moment moment = new Moment();
        moment.setUserId(userId);
        moment.setNickname(nickname);
        moment.setContent(content);
        moment.setLikeCount(0);
        moment.setCommentCount(0);
        moment.setStatus(0);
        moment.setPinned(0);
        momentMapper.insert(moment);
        return moment;
    }

    /** 软删除（本人删除自己的动态；其下评论级联软删，防止孤儿数据仍可查） */
    public boolean softDelete(Long id, Long userId) {
        Moment moment = momentMapper.selectById(id);
        if (moment == null || moment.getStatus() == 2) return false;
        if (!moment.getUserId().equals(userId)) return false;
        momentMapper.update(null,
                new LambdaUpdateWrapper<Moment>()
                        .eq(Moment::getId, id)
                        .set(Moment::getStatus, 2));
        // 级联软删该动态下的全部评论
        commentMapper.update(null,
                new LambdaUpdateWrapper<Comment>()
                        .eq(Comment::getMomentId, id)
                        .set(Comment::getStatus, 2));
        return true;
    }

    /**
     * 点赞/取消点赞。返回 true=已赞，false=已取消。
     * 赞时通知动态作者。
     */
    @Transactional
    public boolean toggleLike(Long momentId, Long userId, String nickname) {
        Moment moment = momentMapper.selectById(momentId);
        if (moment == null || moment.getStatus() != 0) return false;

        MomentLike existing = likeMapper.selectOne(
                new LambdaQueryWrapper<MomentLike>()
                        .eq(MomentLike::getMomentId, momentId)
                        .eq(MomentLike::getUserId, userId));

        if (existing != null) {
            likeMapper.deleteById(existing.getId());
            // 原子递减，避免并发读改写丢失更新
            momentMapper.update(null,
                    new LambdaUpdateWrapper<Moment>()
                            .eq(Moment::getId, momentId)
                            .setSql("like_count = GREATEST(0, like_count - 1)"));
            return false;
        } else {
            MomentLike like = new MomentLike();
            like.setMomentId(momentId);
            like.setUserId(userId);
            like.setNickname(nickname);
            try {
                likeMapper.insert(like);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发点赞：唯一约束下已有赞记录，幂等视为已赞
                return true;
            }
            // 原子递增，避免并发读改写丢失更新
            momentMapper.update(null,
                    new LambdaUpdateWrapper<Moment>()
                            .eq(Moment::getId, momentId)
                            .setSql("like_count = like_count + 1"));
            if (!moment.getUserId().equals(userId)) {
                notificationService.sendLike(moment.getUserId(), momentId,
                        nickname + " 赞了你的动态");
            }
            return true;
        }
    }

    /** 点赞列表（谁赞了），按时间倒序 */
    public PageResult<MomentLike> likes(Long momentId, int page, int size) {
        Page<MomentLike> p = new Page<>(page, size);
        Page<MomentLike> result = likeMapper.selectPage(p,
                new LambdaQueryWrapper<MomentLike>()
                        .eq(MomentLike::getMomentId, momentId)
                        .orderByDesc(MomentLike::getCreatedAt));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    /** 批量填充当前用户点赞状态（避免N+1） */
    private void fillLikedState(List<Moment> records, Long currentUserId) {
        if (currentUserId == null || records.isEmpty()) return;
        List<Long> ids = records.stream().map(Moment::getId).collect(Collectors.toList());
        Set<Long> likedIds = likeMapper.selectList(
                        new LambdaQueryWrapper<MomentLike>()
                                .eq(MomentLike::getUserId, currentUserId)
                                .in(MomentLike::getMomentId, ids))
                .stream().map(MomentLike::getMomentId).collect(Collectors.toSet());
        for (Moment m : records) {
            m.setLiked(likedIds.contains(m.getId()));
        }
    }
}
