package com.recloud.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小说 VO
 */
@Data
@Schema(description = "小说信息")
public class NovelVO {

    @Schema(description = "小说ID")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "作者")
    private String author;

    @Schema(description = "简介")
    private String description;

    @Schema(description = "文件名")
    private String fileName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
