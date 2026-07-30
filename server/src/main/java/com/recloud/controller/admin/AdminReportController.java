package com.recloud.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.recloud.common.annotation.Log;
import com.recloud.common.result.R;
import com.recloud.security.SecurityUtils;
import com.recloud.service.ReportService;
import com.recloud.vo.AdminReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理端-举报管理", description = "举报列表/处理")
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportService reportService;

    @Operation(summary = "举报列表（分页+状态筛选，含举报人/处理人昵称）")
    @GetMapping
    public R<IPage<AdminReportVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return R.ok(reportService.listAdminReports(page, size, status));
    }

    @Operation(summary = "处理举报")
    @PutMapping("/{id}/handle")
    @Log(module = "举报管理", operation = "处理举报")
    public R<Map<String, Object>> handle(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String handleNote) {
        Long handlerId = SecurityUtils.getCurrentUserId();
        reportService.handleReport(id, handlerId, status, handleNote);
        return R.ok(Map.of("success", true));
    }
}
