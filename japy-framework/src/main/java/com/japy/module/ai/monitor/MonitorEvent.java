package com.japy.module.ai.monitor;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** 检测器输出的事件（规则层事实，LLM 解读的输入） */
@Data
@Builder
public class MonitorEvent {
    private String monitorCode;
    private String monitorName;
    /** 1信息 2警告 3严重 */
    private int severity;
    /** 去重指纹：code:关键维度（如 IP/接口） */
    private String fingerprint;
    /** 事实描述（人可读） */
    private String summary;
    /** 结构化证据 */
    private Map<String, Object> evidence;
}
