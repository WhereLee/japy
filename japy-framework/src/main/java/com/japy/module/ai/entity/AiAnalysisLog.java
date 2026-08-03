package com.japy.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 分析日志（LLM 调用审计） */
@Data
@TableName("ai_analysis_log")
public class AiAnalysisLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;          // interpret / feedback_analysis
    private Long refId;
    private String promptSummary;
    private String responseSummary;
    private String model;
    private Integer tokenIn;
    private Integer tokenOut;
    private BigDecimal cost;
    private Long costTime;
    private Integer success;         // 0失败 1成功
    private String traceId;
    private LocalDateTime createdAt;
}
