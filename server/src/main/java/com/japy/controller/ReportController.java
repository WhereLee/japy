package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.UserContext;
import com.japy.entity.Report;
import com.japy.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportMapper reportMapper;

    /** 举报帖子/评论 */
    @PostMapping
    public R<Report> create(@RequestBody Report report) {
        if (report.getTargetType() == null || report.getTargetId() == null) {
            return R.fail("举报对象不能为空");
        }
        if (!"post".equals(report.getTargetType()) && !"comment".equals(report.getTargetType())) {
            return R.fail("举报类型无效");
        }
        // 检查是否重复举报
        Long count = reportMapper.selectCount(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, UserContext.getUserId())
                        .eq(Report::getTargetType, report.getTargetType())
                        .eq(Report::getTargetId, report.getTargetId())
                        .eq(Report::getStatus, 0));
        if (count > 0) return R.fail("已举报过，请勿重复提交");

        report.setReporterId(UserContext.getUserId());
        report.setStatus(0);
        reportMapper.insert(report);
        return R.ok(report);
    }

    /** 我的举报记录 */
    @GetMapping("/my")
    public R<PageResult<Report>> myReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        Page<Report> result = reportMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, userId)
                        .orderByDesc(Report::getCreatedAt));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }
}
