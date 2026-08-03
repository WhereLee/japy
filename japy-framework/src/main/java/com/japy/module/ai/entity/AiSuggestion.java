package com.japy.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 建议卡（L2 人工审批载体） */
@Data
@TableName("ai_suggestion")
public class AiSuggestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private String action;           // 建议动作
    private String impact;           // 影响评估
    private String risk;             // 风险
    private Integer status;          // 0待审 1已批准 2已驳回 3已执行
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
}
