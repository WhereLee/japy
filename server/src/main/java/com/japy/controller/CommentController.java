package com.japy.controller;

import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.UserContext;
import com.japy.entity.Comment;
import com.japy.mapper.CommentMapper;
import com.japy.service.CommentService;
import com.japy.service.PostService;
import com.japy.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final PostService postService;
    private final CommentMapper commentMapper;
    private final SensitiveWordService sensitiveWordService;

    @GetMapping
    public R<PageResult<Comment>> list(
            @RequestParam Long postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return R.ok(commentService.listByPost(postId, page, size));
    }

    @PostMapping
    public R<Comment> create(@RequestBody Comment comment) {
        if (comment.getContent() == null || comment.getContent().isBlank()) {
            return R.fail("内容不能为空");
        }
        if (comment.getPostId() == null) {
            return R.fail("帖子ID不能为空");
        }
        String hit = sensitiveWordService.check(comment.getContent());
        if (hit != null) return R.fail("内容包含敏感词：" + hit);
        comment.setUserId(UserContext.getUserId());
        comment.setNickname(UserContext.getNickname());
        Comment saved = commentService.create(comment);
        postService.incrementCommentCount(comment.getPostId());
        return R.ok(saved);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) return R.fail("评论不存在");
        if (!comment.getUserId().equals(UserContext.getUserId())) {
            return R.fail("只能删除自己的评论");
        }
        commentService.softDelete(id);
        return R.ok();
    }
}
