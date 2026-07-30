package com.recloud.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端举报 VO（含举报人昵称、处理人昵称等可读信息）
 * <p>
 * 由 XML 联表查询（content_report LEFT JOIN user）一次产出，避免暴露原始实体并消除 N+1。
 */
@Data
@Schema(description = "管理端-举报信息")
public class AdminReportVO {

    @Schema(description = "举报ID")
    private Long id;

    @Schema(description = "举报人ID")
    private Long reporterId;

    @Schema(description = "举报人昵称")
    private String reporterNickname;

    @Schema(description = "目标类型 annotation/comment")
    private String targetType;

    @Schema(description = "目标ID")
    private Long targetId;

    @Schema(description = "举报原因")
    private String reason;

    @Schema(description = "状态 pending/resolved/rejected")
    private String status;

    @Schema(description = "处理人ID")
    private Long handlerId;

    @Schema(description = "处理人昵称")
    private String handlerNickname;

    @Schema(description = "处理备注")
    private String handleNote;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "举报时间")
    private LocalDateTime createdAt;
}
