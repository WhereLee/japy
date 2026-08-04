package com.japy.module.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.aspect.OperLog;
import com.japy.aspect.RateLimit;
import com.japy.common.R;
import com.japy.module.ai.dto.AiDtos;
import com.japy.module.ai.entity.AiFeedbackInsight;
import com.japy.module.ai.entity.AiMonitorEvent;
import com.japy.module.ai.entity.AiSuggestion;
import com.japy.module.ai.entity.SysNotification;
import com.japy.module.ai.mapper.AiFeedbackInsightMapper;
import com.japy.module.ai.mapper.AiMonitorEventMapper;
import com.japy.module.ai.mapper.SysNotificationMapper;
import com.japy.module.ai.service.AiFeedbackService;
import com.japy.module.ai.service.AiMonitorService;
import com.japy.module.ai.service.AiReportService;
import com.japy.module.ai.service.AiSuggestionService;
import com.japy.common.BusinessException;
import com.japy.common.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 运维管理端接口：报告 / 信号 / 建议卡 / 反馈 / 洞察 / 通知。
 * 全部仅管理员角色（RBAC ai:* 权限），写操作进操作审计。
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiAdminController {

    private final AiMonitorService monitorService;
    private final AiReportService reportService;
    private final AiSuggestionService suggestionService;
    private final AiFeedbackService feedbackService;
    private final AiMonitorEventMapper eventMapper;
    private final AiFeedbackInsightMapper insightMapper;
    private final SysNotificationMapper notificationMapper;

    // ---------- 报告 ----------

    @GetMapping("/report")
    @PreAuthorize("@ss.hasPermi('ai:report:list')")
    public R<Map<String, Object>> report() {
        return R.ok(reportService.report());
    }

    // ---------- 信号 ----------

    /** 手动触发一轮检测（调试/演示用） */
    @PostMapping("/events/run")
    @PreAuthorize("@ss.hasPermi('ai:event:run')")
    @OperLog(title = "AI 监测", businessType = 1)
    public R<Integer> runNow() {
        return R.ok(monitorService.checkAll());
    }

    @GetMapping("/events")
    @PreAuthorize("@ss.hasPermi('ai:event:list')")
    public R<IPage<AiMonitorEvent>> events(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String monitorCode) {
        LambdaQueryWrapper<AiMonitorEvent> qw = new LambdaQueryWrapper<>();
        qw.eq(status != null, AiMonitorEvent::getStatus, status)
                .eq(monitorCode != null && !monitorCode.isBlank(), AiMonitorEvent::getMonitorCode, monitorCode)
                .orderByDesc(AiMonitorEvent::getId);
        return R.ok(eventMapper.selectPage(new Page<>(page, size), qw));
    }

    /** 确认（问题已处理/已知晓） */
    @PostMapping("/events/{id}/confirm")
    @PreAuthorize("@ss.hasPermi('ai:event:confirm')")
    @OperLog(title = "AI 监测", businessType = 2)
    public R<Void> confirm(@PathVariable Long id) {
        setEventStatus(id, 2);
        return R.ok();
    }

    /** 忽略（误报） */
    @PostMapping("/events/{id}/ignore")
    @PreAuthorize("@ss.hasPermi('ai:event:confirm')")
    @OperLog(title = "AI 监测", businessType = 2)
    public R<Void> ignore(@PathVariable Long id) {
        setEventStatus(id, 3);
        return R.ok();
    }

    private void setEventStatus(Long id, int status) {
        AiMonitorEvent e = eventMapper.selectById(id);
        if (e == null) {
            throw new BusinessException("信号不存在");
        }
        e.setStatus(status);
        e.setConfirmedBy(SecurityUtils.userId());
        e.setConfirmedAt(LocalDateTime.now());
        eventMapper.updateById(e);
    }

    // ---------- 建议卡 ----------

    @GetMapping("/suggestions")
    @PreAuthorize("@ss.hasPermi('ai:suggestion:handle')")
    public R<IPage<AiSuggestion>> suggestions(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) Integer status) {
        return R.ok(suggestionService.list(status, page, size));
    }

    @PostMapping("/suggestions/{id}/approve")
    @PreAuthorize("@ss.hasPermi('ai:suggestion:handle')")
    @OperLog(title = "AI 建议卡", businessType = 2)
    public R<Void> approve(@PathVariable Long id) {
        suggestionService.approve(id);
        return R.ok();
    }

    @PostMapping("/suggestions/{id}/reject")
    @PreAuthorize("@ss.hasPermi('ai:suggestion:handle')")
    @OperLog(title = "AI 建议卡", businessType = 2)
    public R<Void> reject(@PathVariable Long id) {
        suggestionService.reject(id);
        return R.ok();
    }

    @PostMapping("/suggestions/{id}/execute")
    @PreAuthorize("@ss.hasPermi('ai:suggestion:handle')")
    @OperLog(title = "AI 建议卡", businessType = 2)
    public R<Void> execute(@PathVariable Long id) {
        suggestionService.execute(id);
        return R.ok();
    }

    // ---------- 反馈闭环 ----------

    @PostMapping("/feedback")
    @PreAuthorize("@ss.hasPermi('ai:feedback:add')")
    @RateLimit(permitsPerSecond = 5, key = "ai-feedback")
    @OperLog(title = "AI 反馈", businessType = 1)
    public R<Void> feedback(@Valid @RequestBody AiDtos.FeedbackDTO dto) {
        feedbackService.submit(dto.getTargetType(), dto.getTargetId(), dto.getRating(),
                dto.getReasonTag(), dto.getComment());
        return R.ok();
    }

    @GetMapping("/feedback/stats")
    @PreAuthorize("@ss.hasPermi('ai:feedback:add')")
    public R<java.util.List<Map<String, Object>>> feedbackStats() {
        return R.ok(feedbackService.stats());
    }

    @GetMapping("/feedback/hint")
    @PreAuthorize("@ss.hasPermi('ai:feedback:add')")
    public R<Map<String, Object>> thresholdHint(@RequestParam String monitorCode) {
        return R.ok(feedbackService.thresholdHint(monitorCode));
    }

    // ---------- 反馈洞察（路径④）----------

    /** 手动触发：LLM 分析近 7 天反馈 → 洞察（待人工应用） */
    @PostMapping("/insight/analyze")
    @PreAuthorize("@ss.hasPermi('ai:insight:analyze')")
    @OperLog(title = "AI 反馈分析", businessType = 1)
    public R<AiFeedbackInsight> analyzeInsight() {
        return R.ok(feedbackService.analyzeFeedback());
    }

    @GetMapping("/insight/list")
    @PreAuthorize("@ss.hasPermi('ai:insight:analyze')")
    public R<IPage<AiFeedbackInsight>> insightList(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return R.ok(insightMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AiFeedbackInsight>().orderByDesc(AiFeedbackInsight::getId)));
    }

    // ---------- 站内通知 ----------

    @GetMapping("/notifications")
    @PreAuthorize("@ss.hasPermi('ai:event:list')")
    public R<IPage<SysNotification>> notifications(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return R.ok(notificationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysNotification>().orderByDesc(SysNotification::getId)));
    }
}
