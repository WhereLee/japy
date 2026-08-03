package com.japy.module.ai.dto;

import com.japy.aspect.Xss;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** AI 运维模块 DTO */
public class AiDtos {

    /** 提交反馈 */
    @Data
    public static class FeedbackDTO {
        @NotBlank(message = "反馈对象类型不能为空")
        private String targetType;          // event / suggestion
        @NotNull(message = "反馈对象 id 不能为空")
        private Long targetId;
        @NotNull(message = "评分不能为空")
        private Integer rating;             // 1好评 0差评
        private String reasonTag;           // 误报/判断错误/建议不可行/信息有用/已按建议处理
        @Xss
        private String comment;             // 自由文本（核心反馈方式）
    }
}
