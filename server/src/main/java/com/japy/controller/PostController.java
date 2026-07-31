package com.japy.controller;

import com.japy.common.R;
import com.japy.common.UserContext;
import com.japy.entity.Post;
import com.japy.mapper.PostMapper;
import com.japy.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostMapper postMapper;

    @GetMapping
    public R<List<Post>> list(@RequestParam Long novelId) {
        return R.ok(postService.listByNovel(novelId));
    }

    @PostMapping
    public R<Post> create(@RequestBody Post post) {
        if (post.getContent() == null || post.getContent().isBlank()) {
            return R.fail("内容不能为空");
        }
        if (post.getNovelId() == null) {
            return R.fail("请选择一本小说");
        }
        // 从登录上下文获取身份
        post.setUserId(UserContext.getUserId());
        post.setNickname(UserContext.getNickname());
        return R.ok(postService.create(post));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) return R.fail("帖子不存在");
        // 只能删自己的
        if (!post.getUserId().equals(UserContext.getUserId())) {
            return R.fail("只能删除自己的帖子");
        }
        postService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/like")
    public R<Map<String, Object>> like(@PathVariable Long id) {
        String nickname = UserContext.getNickname();
        boolean liked = postService.toggleLike(id, nickname);
        return R.ok(Map.of("liked", liked));
    }
}
