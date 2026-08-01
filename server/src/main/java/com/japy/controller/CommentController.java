package com.japy.controller;

import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.UserContext;
import com.japy.entity.Comment;
import com.japy.entity.Post;
import com.japy.mapper.CommentMapper;
import com.japy.mapper.PostMapper;
import com.japy.service.CommentService;
import com.japy.service.NotificationService;
import com.japy.service.PointsService;
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
    private final NotificationService notificationService;
    private final PostMapper postMapper;
    private final PointsService pointsService;

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
        // 检查帖子是否存在且正常
        Post targetPost = postMapper.selectById(comment.getPostId());
        if (targetPost == null || targetPost.getStatus() != 0) {
            return R.fail("帖子不存在");
        }
        String hit = sensitiveWordService.check(comment.getContent());
        if (hit != null) return R.fail("内容包含敏感词：" + hit);
        comment.setUserId(UserContext.getUserId());
        comment.setNickname(UserContext.getNickname());
        Comment saved = commentService.create(comment);
        postService.incrementCommentCount(comment.getPostId());
        // 积分：发评论+1
        pointsService.earn(comment.getUserId(), "comment", 1);
        // 通知帖子作者
        Post post = postMapper.selectById(comment.getPostId());
        if (post != null && post.getUserId() != null && !post.getUserId().equals(UserContext.getUserId())) {
            notificationService.send(post.getUserId(), "comment", "post", post.getId(),
                    UserContext.getNickname() + " 评论了你的帖子");
        }
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
