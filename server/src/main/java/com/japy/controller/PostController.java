package com.japy.controller;

import com.japy.common.R;
import com.japy.entity.Post;
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

    @GetMapping
    public R<List<Post>> list(@RequestParam Long novelId) {
        return R.ok(postService.listByNovel(novelId));
    }

    @PostMapping
    public R<Post> create(@RequestBody Post post) {
        if (post.getNickname() == null || post.getNickname().isBlank()) {
            return R.fail("昵称不能为空");
        }
        if (post.getContent() == null || post.getContent().isBlank()) {
            return R.fail("内容不能为空");
        }
        if (post.getNovelId() == null) {
            return R.fail("请选择一本小说");
        }
        return R.ok(postService.create(post));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/like")
    public R<Map<String, Object>> like(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String nickname = body.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            return R.fail("昵称不能为空");
        }
        boolean liked = postService.toggleLike(id, nickname);
        return R.ok(Map.of("liked", liked));
    }
}
