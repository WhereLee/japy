package com.recloud.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.recloud.common.annotation.Log;
import com.recloud.common.result.R;
import com.recloud.common.result.ResultCode;
import com.recloud.service.CommentService;
import com.recloud.vo.AdminCommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-评论管理", description = "评论列表/删除")
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommentController {

    private final CommentService commentService;

    @Operation(summary = "评论列表（分页+关键词搜索，含评论者昵称/批注原文）")
    @GetMapping
    public R<IPage<AdminCommentVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return R.ok(commentService.listAdminComments(page, size, keyword));
    }

    @Operation(summary = "管理员删除评论")
    @DeleteMapping("/{id}")
    @Log(module = "评论管理", operation = "管理员删除评论")
    public R<String> delete(@PathVariable Long id) {
        boolean success = commentService.adminDeleteComment(id);
        return success ? R.ok("删除成功") : R.fail(ResultCode.COMMENT_NOT_FOUND);
    }
}
