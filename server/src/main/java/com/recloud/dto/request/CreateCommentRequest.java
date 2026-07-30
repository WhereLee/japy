package com.recloud.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建评论请求")
public class CreateCommentRequest {

    @NotNull(message = "批注ID不能为空")
    @Schema(description = "批注ID")
    private Long annotationId;

    @NotBlank(message = "评论内容不能为空")
    @Size(min = 1, max = 500, message = "评论内容长度1-500字符")
    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "回复的评论ID（可选）")
    private Long replyToId;
}
