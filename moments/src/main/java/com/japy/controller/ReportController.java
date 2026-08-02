package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.PageParams;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.UserContext;
import com.japy.entity.Comment;
import com.japy.entity.Moment;
import com.japy.entity.Report;
import com.japy.mapper.CommentMapper;
import com.japy.mapper.MomentMapper;
import com.japy.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportMapper reportMapper;
    private final MomentMapper momentMapper;
    private final CommentMapper commentMapper;

    /** 举报动态/评论 */
    @PostMapping
    public R<Report> create(@RequestBody Report report) {
        if (report.getTargetType() == null || report.getTargetId() == null) {
            return R.fail("举报对象不能为空");
        }
        if (!"moment".equals(report.getTargetType()) && !"comment".equals(report.getTargetType())) {
            return R.fail("举报类型无效");
        }
        // 校验目标存在
        Long authorId = null;
        if ("moment".equals(report.getTargetType())) {
            Moment moment = momentMapper.selectById(report.getTargetId());
            if (moment == null || moment.getStatus() == 2) return R.fail("动态不存在");
            authorId = moment.getUserId();
        } else {
            Comment comment = commentMapper.selectById(report.getTargetId());
            if (comment == null || comment.getStatus() == 2) return R.fail("评论不存在");
            authorId = comment.getUserId();
        }
        // 禁止举报自己的内容
        if (UserContext.getUserId().equals(authorId)) {
            return R.fail("不能举报自己的内容");
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
        Page<Report> result = reportMapper.selectPage(new Page<>(PageParams.page(page), PageParams.size(size)),
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, userId)
                        .orderByDesc(Report::getCreatedAt));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }
}
