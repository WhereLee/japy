package com.japy.controller;

import com.japy.common.PageParams;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.UserContext;
import com.japy.entity.Comment;
import com.japy.service.CommentService;
import com.japy.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final SensitiveWordService sensitiveWordService;

    /** 评论列表（顶层分页 + 楼中楼子回复分页） */
    @GetMapping
    public R<PageResult<Map<String, Object>>> list(
            @RequestParam Long momentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "20") int replySize) {
        try {
            return R.ok(commentService.listByMoment(momentId,
                    PageParams.page(page), PageParams.size(size), PageParams.size(replySize)));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 发表评论 / 回复（parentId 非空时） */
    @PostMapping
    public R<Comment> create(@RequestBody Map<String, String> body) {
        Long momentId = body.get("momentId") == null ? null : Long.valueOf(body.get("momentId"));
        Long parentId = body.get("parentId") == null || body.get("parentId").isBlank()
                ? null : Long.valueOf(body.get("parentId"));
        String content = body.get("content");

        if (momentId == null) return R.fail("momentId不能为空");
        if (content == null || content.isBlank()) return R.fail("内容不能为空");
        if (content.length() > 500) return R.fail("评论过长（最多500字）");
        String hit = sensitiveWordService.check(content);
        if (hit != null) return R.fail("内容包含敏感词：" + hit);

        try {
            Comment saved = commentService.create(momentId, UserContext.getUserId(),
                    UserContext.getNickname(), parentId, body.get("replyTo"), content);
            return R.ok(saved);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 删除自己的评论（顶层评论的子回复一并删除） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        if (!commentService.softDelete(id, UserContext.getUserId())) {
            return R.fail("评论不存在或无权删除");
        }
        return R.ok();
    }
}
