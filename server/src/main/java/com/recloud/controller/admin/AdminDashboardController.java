package com.recloud.controller.admin;

import com.recloud.common.result.R;
import com.recloud.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端-数据概览", description = "Dashboard 统计数据")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取仪表盘统计数据")
    @GetMapping
    public R<Map<String, Object>> dashboard() {
        return R.ok(dashboardService.getDashboardData());
    }

    @Operation(summary = "获取指定日期日报（格式: yyyy-MM-dd）")
    @GetMapping("/daily-report")
    public R<Map<String, Object>> dailyReport(@RequestParam String date) {
        return R.ok(dashboardService.getDailyReport(date));
    }

    @Operation(summary = "获取最近 N 天日报列表")
    @GetMapping("/daily-reports")
    public R<List<Map<String, Object>>> dailyReports(
            @RequestParam(defaultValue = "7") int days) {
        return R.ok(dashboardService.getRecentDailyReports(days));
    }
}
