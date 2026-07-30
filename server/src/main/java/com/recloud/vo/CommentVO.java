package com.recloud.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论 VO
 */
@Data
@Schema(description = "评论信息")
public class CommentVO {

    @Schema(description = "评论ID")
    private Long id;

    @Schema(description = "批注ID")
    private Long annotationId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "回复哪条评论ID，null=直接评论批注")
    private Long replyToId;

    @Schema(description = "评论内容")
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
