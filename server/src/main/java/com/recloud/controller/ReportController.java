package com.recloud.controller;

import com.recloud.common.annotation.Log;
import com.recloud.common.result.R;
import com.recloud.dto.request.CreateReportRequest;
import com.recloud.entity.ContentReport;
import com.recloud.security.SecurityUtils;
import com.recloud.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "举报管理", description = "用户举报违规内容")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "举报内容")
    @PostMapping
    @Log(module = "举报", operation = "举报内容")
    public R<Map<String, Object>> create(@Valid @RequestBody CreateReportRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        ContentReport report = reportService.create(userId, request);
        return R.ok(Map.of("id", report.getId(), "status", report.getStatus()));
    }
}
