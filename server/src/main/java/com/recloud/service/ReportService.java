package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import com.recloud.dto.request.CreateReportRequest;
import com.recloud.entity.Annotation;
import com.recloud.entity.AnnotationLike;
import com.recloud.entity.Comment;
import com.recloud.entity.ContentReport;
import com.recloud.mapper.AnnotationLikeMapper;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.mapper.CommentMapper;
import com.recloud.mapper.ContentReportMapper;
import com.recloud.vo.AdminReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ContentReportMapper reportMapper;
    private final AnnotationMapper annotationMapper;
    private final AnnotationLikeMapper likeMapper;
    private final CommentMapper commentMapper;
    private final NotificationService notificationService;

    private static final List<String> VALID_TARGET_TYPES = Arrays.asList("annotation", "comment");

    /**
     * 创建举报
     */
    @Transactional(rollbackFor = Exception.class)
    public ContentReport create(Long reporterId, CreateReportRequest request) {
        String targetType = request.getTargetType();
        Long targetId = request.getTargetId();

        // 校验目标类型
        if (!VALID_TARGET_TYPES.contains(targetType)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "举报目标类型无效");
        }

        // 校验目标是否存在
        if ("annotation".equals(targetType)) {
            if (annotationMapper.selectById(targetId) == null) {
                throw new BizException(ResultCode.ANNOTATION_NOT_FOUND);
            }
        } else {
            if (commentMapper.selectById(targetId) == null) {
                throw new BizException(ResultCode.COMMENT_NOT_FOUND);
            }
        }

        // 防重复举报：同一用户对同一目标只能举报一次（pending 状态）
        Long exists = reportMapper.selectCount(
                new LambdaQueryWrapper<ContentReport>()
                        .eq(ContentReport::getReporterId, reporterId)
                        .eq(ContentReport::getTargetType, targetType)
                        .eq(ContentReport::getTargetId, targetId)
                        .eq(ContentReport::getStatus, "pending")
        );
        if (exists > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "您已举报过该内容，请等待处理");
        }

        ContentReport report = new ContentReport();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(request.getReason());
        report.setStatus("pending");
        reportMapper.insert(report);

        log.info("举报创建: reporterId={}, targetType={}, targetId={}", reporterId, targetType, targetId);
        return report;
    }

    /**
     * 管理员分页查询举报列表（含举报人/处理人昵称）
     * <p>
     * 走 XML 联表查询，一次 SQL 完成多表关联，避免 N+1。
     */
    public IPage<AdminReportVO> listAdminReports(int page, int size, String status) {
        Page<AdminReportVO> pageParam = new Page<>(page, size);
        return reportMapper.selectAdminReportPage(pageParam, status);
    }

    /**
     * 管理员处理举报
     * <p>
     * 完整业务编排：更新举报状态 →（成立时）删除违规内容 → 通知举报人。
     * 举报不存在时抛 {@link ResultCode#REPORT_NOT_FOUND}，由全局异常处理器统一返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long reportId, Long handlerId, String status, String handleNote) {
        ContentReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ResultCode.REPORT_NOT_FOUND);
        }

        report.setStatus(status);
        report.setHandlerId(handlerId);
        report.setHandleNote(handleNote);
        reportMapper.updateById(report);

        // 举报成立：删除违规内容
        if ("resolved".equals(status)) {
            deleteReportedContent(report.getTargetType(), report.getTargetId());
        }

        // 通知举报人处理结果
        if ("resolved".equals(status)) {
            notificationService.sendReportResolvedNotification(report.getReporterId(), reportId);
        } else if ("rejected".equals(status)) {
            notificationService.sendReportRejectedNotification(report.getReporterId(), reportId, handleNote);
        }

        log.info("举报处理: reportId={}, status={}, handlerId={}", reportId, status, handlerId);
    }

    /**
     * 删除被举报的违规内容（级联删除评论+点赞）
     */
    private void deleteReportedContent(String targetType, Long targetId) {
        if ("annotation".equals(targetType)) {
            Annotation ann = annotationMapper.selectById(targetId);
            if (ann != null) {
                // 级联删除评论、点赞、批注
                commentMapper.delete(
                        new LambdaQueryWrapper<Comment>().eq(Comment::getAnnotationId, targetId));
                likeMapper.delete(
                        new LambdaQueryWrapper<AnnotationLike>().eq(AnnotationLike::getAnnotationId, targetId));
                annotationMapper.deleteById(targetId);
            }
        } else {
            Comment comment = commentMapper.selectById(targetId);
            if (comment != null) {
                // 级联删除子回复
                commentMapper.delete(
                        new LambdaQueryWrapper<Comment>().eq(Comment::getReplyToId, targetId));
                commentMapper.deleteById(targetId);
            }
        }
    }
}
