package com.recloud.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建批注请求")
public class CreateAnnotationRequest {

    @NotNull(message = "章节ID不能为空")
    @Schema(description = "章节ID")
    private Long chapterId;

    @NotNull(message = "起始偏移不能为空")
    @Min(value = 0, message = "起始偏移不能为负数")
    @Schema(description = "选区起始偏移")
    private Integer anchorStart;

    @NotNull(message = "结束偏移不能为空")
    @Schema(description = "选区结束偏移")
    private Integer anchorEnd;

    @NotBlank(message = "选中文本不能为空")
    @Size(max = 500, message = "选中文本不能超过500字符")
    @Schema(description = "选中的文本")
    private String selectedText;

    @NotBlank(message = "批注内容不能为空")
    @Size(max = 2000, message = "批注内容不能超过2000字符")
    @Schema(description = "批注内容")
    private String content;

    @Schema(description = "批注类型：0=普通 1=数据校验", example = "0")
    private Integer type;
}
