package com.recloud.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端评论 VO（含评论者昵称、所评批注的原文上下文）
 * <p>
 * 由 XML 联表查询（annotation_comment LEFT JOIN user LEFT JOIN annotation）一次产出，
 * 避免暴露原始实体并提供审核所需的上下文信息。
 */
@Data
@Schema(description = "管理端-评论信息")
public class AdminCommentVO {

    @Schema(description = "评论ID")
    private Long id;

    @Schema(description = "所属批注ID")
    private Long annotationId;

    @Schema(description = "评论者ID")
    private Long userId;

    @Schema(description = "评论者昵称")
    private String userNickname;

    @Schema(description = "回复的评论ID（NULL=直接评论批注）")
    private Long replyToId;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "所评批注的原文片段（审核上下文）")
    private String annotationSelectedText;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "评论时间")
    private LocalDateTime createdAt;
}
