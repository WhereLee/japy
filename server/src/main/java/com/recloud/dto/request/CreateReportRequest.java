package com.recloud.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "举报请求")
public class CreateReportRequest {

    @NotBlank(message = "举报目标类型不能为空")
    @Schema(description = "举报目标类型：annotation/comment")
    private String targetType;

    @NotNull(message = "举报目标ID不能为空")
    @Schema(description = "举报目标ID")
    private Long targetId;

    @NotBlank(message = "举报原因不能为空")
    @Size(min = 2, max = 500, message = "举报原因长度2-500字符")
    @Schema(description = "举报原因")
    private String reason;
}
