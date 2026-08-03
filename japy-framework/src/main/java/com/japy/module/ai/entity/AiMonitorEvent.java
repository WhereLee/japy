package com.japy.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 监测信号（规则层输出的事实） */
@Data
@TableName("ai_monitor_event")
public class AiMonitorEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String monitorCode;
    private String monitorName;
    private Integer severity;        // 1信息 2警告 3严重
    private String fingerprint;      // 去重指纹（code+关键维度）
    private String summary;          // 规则层事实描述
    private String evidence;         // 证据 JSON
    private Integer status;          // 0待解读 1已解读 2已确认 3已忽略
    private String insight;          // LLM 解读
    private String rootCause;        // 根因推测
    private String suggestion;       // 建议动作草稿
    private BigDecimal confidence;   // 置信度
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
}
