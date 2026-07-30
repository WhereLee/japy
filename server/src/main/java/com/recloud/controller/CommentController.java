package com.recloud.controller;

import com.recloud.common.annotation.Log;
import com.recloud.common.annotation.RateLimiter;
import com.recloud.common.result.R;
import com.recloud.dto.request.CreateCommentRequest;
import com.recloud.entity.Comment;
import com.recloud.security.SecurityUtils;
import com.recloud.service.CommentService;
import com.recloud.vo.CommentVO;
import com.recloud.vo.VOConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "评论管理", description = "批注评论CRUD")
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "创建评论")
    @PostMapping
    @Log(module = "评论", operation = "创建评论")
    @RateLimiter(limit = 20, time = 60, key = "create_comment")
    public R<CommentVO> create(@Valid @RequestBody CreateCommentRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Comment comment = commentService.create(
                request.getAnnotationId(), userId,
                request.getReplyToId(), request.getContent()
        );
        return R.ok(VOConverter.toVO(comment));
    }

    @Operation(summary = "按批注查询评论列表（分页）")
    @GetMapping
    public R<List<CommentVO>> listByAnnotation(
            @RequestParam Long annotationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(VOConverter.toCommentVOList(
                commentService.listByAnnotation(annotationId, page, size)));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    @Log(module = "评论", operation = "删除评论")
    public R<Map<String, Object>> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean deleted = commentService.deleteComment(id, userId);
        return R.ok(Map.of("success", deleted));
    }
}
