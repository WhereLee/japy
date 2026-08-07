package com.japy.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.common.BusinessException;
import com.japy.common.SecurityUtils;
import com.japy.module.ai.entity.AiAnalysisLog;
import com.japy.module.ai.entity.AiFeedback;
import com.japy.module.ai.entity.AiFeedbackInsight;
import com.japy.module.ai.llm.LlmClient;
import com.japy.module.ai.llm.LlmResponse;
import com.japy.module.ai.llm.LlmUnavailableException;
import com.japy.module.ai.mapper.AiAnalysisLogMapper;
import com.japy.module.ai.mapper.AiFeedbackInsightMapper;
import com.japy.module.ai.mapper.AiFeedbackMapper;
import com.japy.module.ai.monitor.MonitorConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 反馈闭环：人工反馈（自由文本）落库 → 阈值自适应提示 → 反馈分析（LLM 聚类出改进建议）。
 * 反馈是优化依据，不是自动进化——所有改进均由人确认后应用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFeedbackService {

    private final AiFeedbackMapper feedbackMapper;
    private final AiFeedbackInsightMapper insightMapper;
    private final AiAnalysisLogMapper analysisLogMapper;
    private final MonitorConfig cfg;
    private final LlmClient llmClient;
    private final AiPromptService promptService;
    private final ObjectMapper objectMapper;
    @Value("${ai.llm.model:deepseek-v4-flash}")
    private String modelName;

    /** 提交反馈（自由文本为核心） */
    public void submit(String targetType, Long targetId, Integer rating, String reasonTag, String comment) {
        if (rating == null || (rating != 0 && rating != 1)) {
            throw new BusinessException("评分仅支持 0（差评）/1（好评）");
        }
        AiFeedback f = new AiFeedback();
        f.setTargetType(targetType);
        f.setTargetId(targetId);
        f.setUserId(SecurityUtils.userId());
        f.setRating(rating);
        f.setReasonTag(reasonTag);
        f.setComment(comment);
        feedbackMapper.insert(f);
    }

    /** 按检测器统计反馈指标（进周报） */
    public List<Map<String, Object>> stats() {
        return feedbackMapper.statsByMonitor();
    }

    /**
     * 阈值自适应提示：某检测器近 7 天"误报"反馈达 N 条 → 提示阈值可能过严并给出建议值。
     * 规则计算建议值（当前阈值 × 1.5 或 +2），人确认后在参数管理调整。
     */
    public Map<String, Object> thresholdHint(String monitorCode) {
        int hintCount = cfg.getInt("monitor.thresholdHint.count", 3);
        long falsePositive = feedbackMapper.selectCount(new LambdaQueryWrapper<AiFeedback>()
                .eq(AiFeedback::getReasonTag, "误报")
                .ge(AiFeedback::getCreatedAt, LocalDateTime.now().minusDays(7)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("monitorCode", monitorCode);
        result.put("falsePositiveCount", falsePositive);
        result.put("hintThreshold", hintCount);
        result.put("hinted", falsePositive >= hintCount);
        if (falsePositive >= hintCount) {
            result.put("message", "检测器 " + monitorCode + " 近 7 天被反馈 " + falsePositive
                    + " 次误报，阈值可能过严，建议到参数管理调整相关阈值");
        }
        return result;
    }

    /**
     * 反馈分析（路径④）：收集近 7 天自由文本反馈 → LLM 聚类 → 反馈洞察（待人工应用）。
     * 当前手动触发（管理端按钮）；后续升级为每周自动调度。
     */
    public AiFeedbackInsight analyzeFeedback() {
        if (!llmClient.available()) {
            throw new BusinessException("LLM 未配置（ai.llm.api-key），无法进行反馈分析");
        }
        List<AiFeedback> list = feedbackMapper.selectList(new LambdaQueryWrapper<AiFeedback>()
                .ge(AiFeedback::getCreatedAt, LocalDateTime.now().minusDays(7))
                .orderByDesc(AiFeedback::getId));
        if (list.isEmpty()) {
            throw new BusinessException("近 7 天暂无反馈可分析");
        }
        String userPrompt = list.stream()
                .map(f -> String.format("- [%s] %s | 标签: %s | 内容: %s",
                        f.getTargetType(), f.getRating() == 1 ? "好评" : "差评",
                        f.getReasonTag() == null ? "-" : f.getReasonTag(),
                        f.getComment() == null ? "-" : f.getComment()))
                .collect(Collectors.joining("\n"));

        AiAnalysisLog audit = new AiAnalysisLog();
        audit.setBizType("feedback_analysis");
        audit.setPromptSummary("反馈 " + list.size() + " 条");
        audit.setModel(modelName);
        long start = System.currentTimeMillis();
        String content;
        try {
            LlmResponse resp = llmClient.chat(promptService.getContent("feedback_analysis"), userPrompt);
            content = resp.getContent();
            audit.setResponseSummary(truncate(content, 1000));
            audit.setTokenIn(resp.getTokenIn());
            audit.setTokenOut(resp.getTokenOut());
            audit.setSuccess(1);
        } catch (LlmUnavailableException e) {
            audit.setResponseSummary(truncate(e.getMessage(), 300));
            audit.setSuccess(0);
            throw new BusinessException("LLM 分析失败：" + e.getMessage());
        } finally {
            audit.setCostTime(System.currentTimeMillis() - start);
            analysisLogMapper.insert(audit);
        }

        AiFeedbackInsight insight = new AiFeedbackInsight();
        insight.setBatchNo(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        insight.setClusterResult(content);
        insight.setImprovement(extractImprovement(content));
        insight.setStatus(0); // 待应用
        insightMapper.insert(insight);
        return insight;
    }

    /** 从 LLM JSON 结果中提取改进建议摘要（供列表展示） */
    private String extractImprovement(String content) {
        try {
            String json = content == null ? "" : content.trim()
                    .replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");
            var node = objectMapper.readTree(json);
            var clusters = node.path("clusters");
            if (clusters.isArray()) {
                return clusters.findValuesAsText("improvement").stream()
                        .collect(Collectors.joining("；"));
            }
        } catch (Exception ignore) {
            // 解析失败则原样返回
        }
        return content == null ? "" : truncate(content, 500);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
