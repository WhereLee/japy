package com.japy.module.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.module.ai.entity.AiAnalysisLog;
import com.japy.module.ai.entity.AiMonitorEvent;
import com.japy.module.ai.entity.AiSuggestion;
import com.japy.module.ai.entity.SysNotification;
import com.japy.module.ai.llm.LlmClient;
import com.japy.module.ai.llm.LlmResponse;
import com.japy.module.ai.llm.LlmUnavailableException;
import com.japy.module.ai.mapper.AiAnalysisLogMapper;
import com.japy.module.ai.mapper.AiMonitorEventMapper;
import com.japy.module.ai.mapper.AiSuggestionMapper;
import com.japy.module.ai.mapper.SysNotificationMapper;
import com.japy.module.ai.monitor.Monitor;
import com.japy.module.ai.monitor.MonitorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 监测主服务：规则检测（L0）→ 去重 → 落库 → 严重即时通知 → LLM 解读（L1）→ 建议卡（L2）。
 * LLM 不可用时整体降级为纯规则模式，不影响检测与通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMonitorService {

    /** 去重窗口（分钟）：同指纹在此窗口内不重复插入 */
    private static final long DEDUP_WINDOW_MIN = 30;

    private final List<Monitor> monitors;
    private final AiMonitorEventMapper eventMapper;
    private final AiSuggestionMapper suggestionMapper;
    private final AiAnalysisLogMapper analysisLogMapper;
    private final SysNotificationMapper notificationMapper;
    private final LlmClient llmClient;
    private final AiPromptService promptService;
    private final ObjectMapper objectMapper;
    @Value("${ai.llm.model:deepseek-v4-flash}")
    private String modelName;

    /** 执行一轮全部检测（调度器与手动触发共用） */
    @Transactional
    public int checkAll() {
        int created = 0;
        for (Monitor monitor : monitors) {
            List<MonitorEvent> events;
            try {
                events = monitor.check();
            } catch (Exception e) {
                // 单个检测器异常不影响其他检测器
                log.warn("检测器 {} 执行异常: {}", monitor.code(), e.getMessage());
                continue;
            }
            for (MonitorEvent event : events) {
                if (eventMapper.countRecentByFingerprint(event.getFingerprint(), DEDUP_WINDOW_MIN) > 0) {
                    continue; // 防通知风暴：同指纹 30 分钟内已存在
                }
                created += persist(event);
            }
        }
        return created;
    }

    private int persist(MonitorEvent event) {
        AiMonitorEvent entity = new AiMonitorEvent();
        entity.setMonitorCode(event.getMonitorCode());
        entity.setMonitorName(event.getMonitorName());
        entity.setSeverity(event.getSeverity());
        entity.setFingerprint(event.getFingerprint());
        entity.setSummary(event.getSummary());
        try {
            entity.setEvidence(objectMapper.writeValueAsString(event.getEvidence()));
        } catch (Exception ignore) {
            entity.setEvidence("{}");
        }
        entity.setStatus(0); // 待解读
        eventMapper.insert(entity);

        if (entity.getSeverity() >= 3) {
            notifySevere(entity); // 严重信号即时通知
        }
        interpret(entity);       // L1：LLM 解读（不可用则保持待解读，降级）
        createSuggestion(entity); // L2：严重度 >=2 生成建议卡
        return 1;
    }

    /** 严重信号即时通知管理员（站内通知） */
    private void notifySevere(AiMonitorEvent event) {
        SysNotification n = new SysNotification();
        n.setUserId(1L); // admin
        n.setTitle("【AI 监测】" + event.getMonitorName());
        n.setContent(event.getSummary());
        notificationMapper.insert(n);
    }

    /** L1：LLM 解读信号（失败记录审计日志并降级，不抛异常） */
    private void interpret(AiMonitorEvent event) {
        if (!llmClient.available()) {
            return;
        }
        String userPrompt = "信号类型：" + event.getMonitorName() + "\n事实描述：" + event.getSummary()
                + "\n证据数据：" + event.getEvidence();
        AiAnalysisLog audit = new AiAnalysisLog();
        audit.setBizType("interpret");
        audit.setRefId(event.getId());
        audit.setPromptSummary(event.getSummary());
        audit.setModel(modelName);
        long start = System.currentTimeMillis();
        try {
            LlmResponse resp = llmClient.chat(promptService.getContent("ops_interpret"), userPrompt);
            audit.setResponseSummary(truncate(resp.getContent(), 1000));
            audit.setTokenIn(resp.getTokenIn());
            audit.setTokenOut(resp.getTokenOut());
            audit.setSuccess(1);
            applyInterpretation(event, resp.getContent());
        } catch (LlmUnavailableException e) {
            audit.setResponseSummary(truncate(e.getMessage(), 300));
            audit.setSuccess(0);
            log.warn("信号 {} 解读失败（降级）: {}", event.getId(), e.getMessage());
        } finally {
            audit.setCostTime(System.currentTimeMillis() - start);
            analysisLogMapper.insert(audit);
        }
    }

    private void applyInterpretation(AiMonitorEvent event, String content) {
        try {
            String json = content == null ? "" : content.trim()
                    .replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");
            JsonNode node = objectMapper.readTree(json);
            event.setInsight(node.path("insight").asText(null));
            event.setRootCause(node.path("rootCause").asText(null));
            event.setSuggestion(node.path("suggestion").asText(null));
            event.setConfidence(node.hasNonNull("confidence")
                    ? BigDecimal.valueOf(node.path("confidence").asDouble(0)) : null);
            event.setStatus(1); // 已解读
            eventMapper.updateById(event);
        } catch (Exception e) {
            log.warn("信号 {} 解读结果解析失败: {}", event.getId(), e.getMessage());
        }
    }

    /** L2：严重度 >= 2 的信号生成建议卡（待人工审批） */
    private void createSuggestion(AiMonitorEvent event) {
        if (event.getSeverity() < 2) {
            return;
        }
        String action = event.getSuggestion();
        if (action == null || action.isBlank()) {
            // LLM 未解读（降级模式）时，以事实描述作为建议依据
            action = event.getSummary() + "（规则模式，建议人工确认处理方案）";
        }
        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setEventId(event.getId());
        suggestion.setAction(action);
        suggestion.setStatus(0); // 待审
        suggestionMapper.insert(suggestion);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
