package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.UserContext;
import com.japy.entity.Post;
import com.japy.entity.PostFavorite;
import com.japy.entity.UserBlock;
import com.japy.mapper.PostFavoriteMapper;
import com.japy.mapper.PostMapper;
import com.japy.mapper.UserBlockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class FavoriteBlockController {

    private final PostFavoriteMapper favoriteMapper;
    private final UserBlockMapper blockMapper;
    private final PostMapper postMapper;

    // ===== 收藏 =====

    @PostMapping("/api/posts/{id}/favorite")
    public R<Map<String, Object>> toggleFavorite(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        PostFavorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .eq(PostFavorite::getPostId, id));

        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return R.ok(Map.of("favorited", false));
        } else {
            PostFavorite fav = new PostFavorite();
            fav.setUserId(userId);
            fav.setPostId(id);
            favoriteMapper.insert(fav);
            return R.ok(Map.of("favorited", true));
        }
    }

    @GetMapping("/api/users/me/favorites")
    public R<PageResult<Post>> myFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        // 先查收藏记录
        Page<PostFavorite> favPage = favoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .orderByDesc(PostFavorite::getCreatedAt));

        List<Long> postIds = favPage.getRecords().stream()
                .map(PostFavorite::getPostId).collect(Collectors.toList());

        List<Post> posts = postIds.isEmpty() ? List.of() :
                postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .in(Post::getId, postIds)
                        .eq(Post::getStatus, 0));

        return R.ok(PageResult.of(posts, favPage.getTotal(), page, size));
    }

    // ===== 屏蔽 =====

    @PostMapping("/api/users/{id}/block")
    public R<Map<String, Object>> toggleBlock(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId.equals(id)) return R.fail("不能屏蔽自己");

        UserBlock existing = blockMapper.selectOne(
                new LambdaQueryWrapper<UserBlock>()
                        .eq(UserBlock::getUserId, userId)
                        .eq(UserBlock::getBlockedUserId, id));

        if (existing != null) {
            blockMapper.deleteById(existing.getId());
            return R.ok(Map.of("blocked", false));
        } else {
            UserBlock block = new UserBlock();
            block.setUserId(userId);
            block.setBlockedUserId(id);
            blockMapper.insert(block);
            return R.ok(Map.of("blocked", true));
        }
    }

    @GetMapping("/api/users/me/blocks")
    public R<List<UserBlock>> myBlocks() {
        Long userId = UserContext.getUserId();
        return R.ok(blockMapper.selectList(
                new LambdaQueryWrapper<UserBlock>()
                        .eq(UserBlock::getUserId, userId)));
    }
}
