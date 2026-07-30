package com.recloud.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.recloud.common.annotation.Log;
import com.recloud.common.result.R;
import com.recloud.common.result.ResultCode;
import com.recloud.service.AnnotationService;
import com.recloud.vo.AdminAnnotationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-批注管理", description = "批注列表/删除")
@RestController
@RequestMapping("/admin/annotations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnnotationController {

    private final AnnotationService annotationService;

    @Operation(summary = "批注列表（分页+搜索+类型筛选，含用户名/章节标题）")
    @GetMapping
    public R<IPage<AdminAnnotationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer type) {
        // 一次 XML 联表查询直接产出含用户昵称/章节标题/小说标题的 VO，无 N+1
        return R.ok(annotationService.listAdminAnnotations(page, size, keyword, type));
    }

    @Operation(summary = "管理员删除批注")
    @DeleteMapping("/{id}")
    @Log(module = "批注管理", operation = "管理员删除批注")
    public R<String> delete(@PathVariable Long id) {
        boolean success = annotationService.adminDeleteAnnotation(id);
        return success ? R.ok("删除成功") : R.fail(ResultCode.ANNOTATION_NOT_FOUND);
    }
}
