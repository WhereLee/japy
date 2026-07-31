package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.UserContext;
import com.japy.entity.Post;
import com.japy.mapper.PostMapper;
import com.japy.service.PostService;
import com.japy.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostMapper postMapper;
    private final SensitiveWordService sensitiveWordService;

    @GetMapping
    public R<PageResult<Post>> list(
            @RequestParam Long novelId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "new") String sort) {
        return R.ok(postService.listByNovel(novelId, page, size, sort));
    }

    @PostMapping
    public R<Post> create(@RequestBody Post post) {
        if (post.getContent() == null || post.getContent().isBlank()) {
            return R.fail("内容不能为空");
        }
        if (post.getNovelId() == null) {
            return R.fail("请选择一本小说");
        }
        String hit = sensitiveWordService.check(post.getContent());
        if (hit != null) return R.fail("内容包含敏感词：" + hit);
        post.setUserId(UserContext.getUserId());
        post.setNickname(UserContext.getNickname());
        return R.ok(postService.create(post));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) return R.fail("帖子不存在");
        if (!post.getUserId().equals(UserContext.getUserId())) {
            return R.fail("只能删除自己的帖子");
        }
        postService.softDelete(id);
        return R.ok();
    }

    @PostMapping("/{id}/like")
    public R<Map<String, Object>> like(@PathVariable Long id) {
        String nickname = UserContext.getNickname();
        boolean liked = postService.toggleLike(id, nickname);
        return R.ok(Map.of("liked", liked));
    }

    /** 搜索帖子 */
    @GetMapping("/search")
    public R<PageResult<Post>> search(
            @RequestParam String q,
            @RequestParam(required = false) Long novelId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<Post> w = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 0)
                .and(wrapper -> wrapper.like(Post::getContent, q).or().like(Post::getQuoteText, q));
        if (novelId != null) w.eq(Post::getNovelId, novelId);
        w.orderByDesc(Post::getCreatedAt);
        Page<Post> result = postMapper.selectPage(new Page<>(page, size), w);
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }
}
