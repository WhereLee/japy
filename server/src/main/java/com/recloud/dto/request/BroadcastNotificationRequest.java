package com.recloud.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "群发全体通知请求")
public class BroadcastNotificationRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "公告标题不超过100字符")
    @Schema(description = "公告标题")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 1000, message = "公告内容不超过1000字符")
    @Schema(description = "公告内容")
    private String content;
}
