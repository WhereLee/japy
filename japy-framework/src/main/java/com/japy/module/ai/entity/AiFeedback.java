package com.japy.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 人工反馈（自由文本为核心） */
@Data
@TableName("ai_feedback")
public class AiFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String targetType;       // event / suggestion
    private Long targetId;
    private Long userId;
    private Integer rating;          // 1好评 0差评
    private String reasonTag;        // 误报/判断错误/建议不可行/信息有用/已按建议处理
    private String comment;          // 自由文本
    private LocalDateTime createdAt;
}
