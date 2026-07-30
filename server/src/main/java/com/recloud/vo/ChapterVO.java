package com.recloud.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节 VO
 */
@Data
@Schema(description = "章节信息")
public class ChapterVO {

    @Schema(description = "章节ID")
    private Long id;

    @Schema(description = "小说ID")
    private Long novelId;

    @Schema(description = "章节标题")
    private String title;

    @Schema(description = "章节内容")
    private String content;

    @Schema(description = "章节序号")
    private Integer chapterOrder;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
