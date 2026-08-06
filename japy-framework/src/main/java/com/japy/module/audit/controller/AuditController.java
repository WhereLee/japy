package com.japy.module.audit.controller;

import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.SecurityUtils;
import com.japy.module.audit.entity.NovelAudit;
import com.japy.module.audit.service.AuditService;
import com.japy.module.novel.service.NovelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内容审核接口（管理端）：
 * 列表 / 待审数 / 确认通过(PASS) / 下架(TAKEDOWN 联动 novelService) / 重新扫描(RESCAN)。
 */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final NovelService novelService;

    /** 审核记录列表（可按 result 过滤） */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('audit:list')")
    public R<PageResult<NovelAudit>> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String result) {
        return R.ok(auditService.listAudits(page, size, result));
    }

    /** 待处理数量（角标） */
    @GetMapping("/pending-count")
    @PreAuthorize("@ss.hasPermi('audit:list')")
    public R<Long> pendingCount() {
        return R.ok(auditService.pendingCount());
    }

    /** 确认通过（合规，无需处置） */
    @PostMapping("/{id}/pass")
    @PreAuthorize("@ss.hasPermi('audit:handle')")
    @com.japy.aspect.OperLog(title = "内容审核", businessType = 2)
    public R<Void> pass(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        auditService.handle(id, "PASS", SecurityUtils.userId(),
                body == null ? null : body.get("remark"));
        return R.ok();
    }

    /** 下架（违规处置：audit 记录 + 联动小说状态 → 下架） */
    @PostMapping("/{id}/takedown")
    @PreAuthorize("@ss.hasPermi('audit:handle')")
    @com.japy.aspect.OperLog(title = "内容审核", businessType = 2)
    public R<Void> takedown(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        NovelAudit audit = auditService.handle(id, "TAKEDOWN", SecurityUtils.userId(),
                body == null ? null : body.get("remark"));
        // 联动：违规下架（audit 域不动 novel，由本层编排）
        novelService.changeStatus(audit.getNovelId(), 1);
        return R.ok();
    }

    /** 重新扫描（多次扫描各自留痕，RESCAN 类型） */
    @PostMapping("/{novelId}/rescan")
    @PreAuthorize("@ss.hasPermi('audit:rescan')")
    @com.japy.aspect.OperLog(title = "内容审核", businessType = 2)
    public R<Integer> rescan(@PathVariable Long novelId) {
        var novel = novelService.adminDetail(novelId);
        int hits = auditService.scanAndRecord(novelId, novel.getTitle(), novel.getIntro(), "RESCAN");
        return R.ok(hits);
    }
}
