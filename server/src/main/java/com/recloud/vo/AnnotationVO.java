package com.recloud.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批注 VO
 */
@Data
@Schema(description = "批注信息")
public class AnnotationVO {

    @Schema(description = "批注ID")
    private Long id;

    @Schema(description = "章节ID")
    private Long chapterId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "选区起始偏移")
    private Integer anchorStart;

    @Schema(description = "选区结束偏移")
    private Integer anchorEnd;

    @Schema(description = "选中的原文")
    private String selectedText;

    @Schema(description = "批注内容")
    private String content;

    @Schema(description = "批注类型 0=普通 1=数据校验")
    private Integer type;

    @Schema(description = "点赞数")
    private Integer likeCount;

    @Schema(description = "评论数")
    private Integer commentCount;

    @Schema(description = "当前用户是否已点赞")
    private Boolean likedByCurrentUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
