package com.japy.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.module.ai.entity.AiMonitorEvent;
import com.japy.module.ai.mapper.AiMonitorEventMapper;
import com.japy.module.ai.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监测报告（L0 规则聚合）：周期内信号统计 + 待处理建议卡 + 反馈指标。
 * 报告即时生成（不落库）；历史对比后续升级。
 */
@Service
@RequiredArgsConstructor
public class AiReportService {

    private final AiMonitorEventMapper eventMapper;
    private final AiSuggestionService suggestionService;
    private final AiFeedbackService feedbackService;
    private final LlmClient llmClient;

    public Map<String, Object> report() {
        LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();
        List<AiMonitorEvent> weekEvents = eventMapper.selectList(new LambdaQueryWrapper<AiMonitorEvent>()
                .ge(AiMonitorEvent::getCreatedAt, weekStart));

        // 按检测器聚合
        Map<String, Map<String, Object>> byMonitor = new LinkedHashMap<>();
        for (AiMonitorEvent e : weekEvents) {
            Map<String, Object> stat = byMonitor.computeIfAbsent(e.getMonitorCode(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", k);
                m.put("name", e.getMonitorName());
                m.put("count", 0);
                m.put("severity", 0);
                return m;
            });
            stat.put("count", ((Number) stat.get("count")).intValue() + 1);
            stat.put("severity", Math.max(((Number) stat.get("severity")).intValue(), e.getSeverity()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", weekStart + " ~ " + LocalDateTime.now());
        result.put("eventTotal", weekEvents.size());
        result.put("events", byMonitor.values());
        result.put("pendingSuggestions", suggestionService.pending().size());
        result.put("feedbackStats", feedbackService.stats());
        result.put("llmAvailable", llmClient.available());
        return result;
    }
}
