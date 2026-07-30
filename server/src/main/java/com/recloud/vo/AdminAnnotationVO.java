package com.recloud.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端批注 VO（包含用户昵称、章节标题、小说标题等可读信息）
 */
@Data
@Schema(description = "管理端-批注信息")
public class AdminAnnotationVO {

    @Schema(description = "批注ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户昵称")
    private String userNickname;

    @Schema(description = "章节ID")
    private Long chapterId;

    @Schema(description = "章节标题")
    private String chapterTitle;

    @Schema(description = "小说标题")
    private String novelTitle;

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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
