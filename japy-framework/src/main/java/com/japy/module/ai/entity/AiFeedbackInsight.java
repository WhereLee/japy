package com.japy.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 反馈洞察（LLM 分析反馈文本后的改进建议） */
@Data
@TableName("ai_feedback_insight")
public class AiFeedbackInsight {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private String clusterResult;    // 聚类结果
    private String improvement;      // 改进建议
    private Integer status;          // 0待应用 1已应用 2已忽略
    private Long appliedBy;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
}
