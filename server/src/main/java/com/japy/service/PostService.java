package com.japy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.PageResult;
import com.japy.entity.Post;
import com.japy.entity.PostLike;
import com.japy.entity.User;
import com.japy.entity.UserBlock;
import com.japy.mapper.PostLikeMapper;
import com.japy.mapper.PostMapper;
import com.japy.mapper.UserBlockMapper;
import com.japy.mapper.UserMapper;
import com.japy.common.UserContext;
import com.japy.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final PostLikeMapper likeMapper;
    private final UserBlockMapper blockMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final PointsService pointsService;

    /**
     * 分页查询帖子（仅正常状态）
     * @param sort "new" 或 "hot"
     */
    public PageResult<Post> listByNovel(Long novelId, int page, int size, String sort) {
        Page<Post> p = new Page<>(page, size);
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getNovelId, novelId)
                .eq(Post::getStatus, 0);

        // 过滤屏蔽用户的帖子
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            List<Long> blockedIds = blockMapper.selectList(
                    new LambdaQueryWrapper<UserBlock>().eq(UserBlock::getUserId, currentUserId))
                    .stream().map(UserBlock::getBlockedUserId).collect(Collectors.toList());
            if (!blockedIds.isEmpty()) {
                wrapper.notIn(Post::getUserId, blockedIds);
            }
        }

        if ("hot".equals(sort)) {
            wrapper.orderByDesc(Post::getPinned)
                   .orderByDesc(Post::getLikeCount)
                   .orderByDesc(Post::getCommentCount)
                   .orderByDesc(Post::getId);
        } else {
            wrapper.orderByDesc(Post::getPinned)
                   .orderByDesc(Post::getCreatedAt)
                   .orderByDesc(Post::getId);
        }

        Page<Post> result = postMapper.selectPage(p, wrapper);
        // 批量填充作者等级（避免 N+1）
        List<Long> userIds = result.getRecords().stream()
                .map(Post::getUserId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (!userIds.isEmpty()) {
            Map<Long, Integer> levelMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u.getLevel() == null ? 0 : u.getLevel()));
            for (Post post : result.getRecords()) {
                if (post.getUserId() != null) {
                    post.setLevel(levelMap.getOrDefault(post.getUserId(), 0));
                }
            }
        }
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public Post create(Post post) {
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(0);
        post.setPinned(0);
        post.setFeatured(0);
        postMapper.insert(post);
        // 积分：发帖+1
        pointsService.earn(post.getUserId(), "post", 1);
        return post;
    }

    /** 软删除 */
    public boolean softDelete(Long id) {
        return postMapper.update(null,
                new LambdaUpdateWrapper<Post>()
                        .eq(Post::getId, id)
                        .set(Post::getStatus, 2)) > 0;
    }

    @Transactional
    public boolean toggleLike(Long postId, String nickname) {
        PostLike existing = likeMapper.selectOne(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getNickname, nickname));

        Post post = postMapper.selectById(postId);
        if (post == null || post.getStatus() != 0) return false;

        if (existing != null) {
            likeMapper.deleteById(existing.getId());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            postMapper.updateById(post);
            return false;
        } else {
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setNickname(nickname);
            likeMapper.insert(like);
            post.setLikeCount(post.getLikeCount() + 1);
            postMapper.updateById(post);
            // 通知帖子作者
            if (post.getUserId() != null && !post.getUserId().equals(UserContext.getUserId())) {
                notificationService.send(post.getUserId(), "like", "post", postId,
                        UserContext.getNickname() + " 赞了你的帖子");
                // 积分：被赞+2
                pointsService.earn(post.getUserId(), "liked", 2);
            }
            return true;
        }
    }

    public void incrementCommentCount(Long postId) {
        postMapper.update(null,
                new LambdaUpdateWrapper<Post>()
                        .eq(Post::getId, postId)
                        .setSql("comment_count = comment_count + 1"));
    }
}
