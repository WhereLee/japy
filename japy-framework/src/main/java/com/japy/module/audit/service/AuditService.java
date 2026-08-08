package com.japy.module.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.common.BusinessException;
import com.japy.common.PageResult;
import com.japy.module.audit.entity.NovelAudit;
import com.japy.module.audit.entity.SensitiveWord;
import com.japy.module.audit.mapper.NovelAuditMapper;
import com.japy.module.audit.mapper.SensitiveWordMapper;
import com.japy.module.novel.entity.NovelParagraph;
import com.japy.module.novel.mapper.NovelParagraphMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容治理服务（audit 域）：
 * - 上传/重扫时用 AC 引擎扫描小说全文（书名+简介+全部段落），命中写 audit 记录
 * - 无命中 → PASS（合规留痕）；有命中 → PENDING（保持上架，人工确认）
 * - 处理：PASS（确认通过）/ TAKEDOWN（下架，由 controller 编排联动 novelService）
 * 边界：audit 域只读 novel 的段落数据，不修改 novel 字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final SensitiveWordMapper wordMapper;
    private final NovelAuditMapper auditMapper;
    private final NovelParagraphMapper paragraphMapper;
    private final AhoCorasick ac;
    private final ObjectMapper om;

    @PostConstruct
    public void init() {
        reloadWords();
    }

    /** 词库变更后重建 AC 自动机 */
    public synchronized void reloadWords() {
        List<SensitiveWord> words = wordMapper.selectList(
                new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getStatus, 0));
        Map<String, String> cat = new HashMap<>();
        words.forEach(w -> cat.put(w.getWord(), w.getCategory()));
        ac.build(cat.keySet(), cat);
    }

    /** 扫描小说全文并留痕（上传/重扫共用）；返回本次命中数 */
    public int scanAndRecord(Long novelId, String title, String intro, String auditType) {
        // 拼全文：书名 + 简介 + 全部段落
        List<NovelParagraph> paras = paragraphMapper.selectList(
                new LambdaQueryWrapper<NovelParagraph>().eq(NovelParagraph::getNovelId, novelId));
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title).append('\n');
        }
        if (intro != null) {
            sb.append(intro).append('\n');
        }
        paras.forEach(p -> sb.append(p.getContent()).append('\n'));

        List<AhoCorasick.Hit> hits = ac.scan(sb.toString());
        NovelAudit audit = new NovelAudit();
        audit.setNovelId(novelId);
        audit.setAuditType(auditType);
        audit.setResult(hits.isEmpty() ? "PASS" : "PENDING");
        audit.setRuleHits(toJson(hits));
        audit.setCreateTime(LocalDateTime.now());
        auditMapper.insert(audit);
        if (!hits.isEmpty()) {
            log.info("小说 {} 扫描命中 {} 词: {}", novelId, hits.size(),
                    hits.stream().map(h -> h.word() + "x" + h.count()).toList());
        }
        return hits.size();
    }

    private String toJson(List<AhoCorasick.Hit> hits) {
        try {
            List<Map<String, Object>> list = hits.stream().map(h -> {
                Map<String, Object> m = new HashMap<>();
                m.put("word", h.word());
                m.put("count", h.count());
                m.put("category", acCategory(h.word()));
                return m;
            }).toList();
            return om.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String acCategory(String word) {
        // AC 引擎内部维护词→类别，这里通过一次查询补齐（或由 AC 暴露）
        SensitiveWord w = wordMapper.selectOne(
                new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getWord, word));
        return w == null ? "其他" : w.getCategory();
    }

    // ==================== 审核查询与处理 ====================

    /** 审核记录分页（按 result 过滤） */
    public PageResult<NovelAudit> listAudits(int page, int size, String result) {
        Page<NovelAudit> p = auditMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<NovelAudit>()
                        .eq(result != null && !result.isBlank(), NovelAudit::getResult, result)
                        .orderByDesc(NovelAudit::getCreateTime));
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    /** 待处理数量（管理端角标） */
    public long pendingCount() {
        return auditMapper.selectCount(
                new LambdaQueryWrapper<NovelAudit>().eq(NovelAudit::getResult, "PENDING"));
    }

    /** 处理审核：PASS（确认通过）/ TAKEDOWN（下架）。
     *  幂等：条件更新（WHERE result='PENDING'），影响 0 行 = 已被他人处理——
     *  防双管理员/双击重复处理（读-判-写原子化，无需悲观锁）。 */
    @org.springframework.transaction.annotation.Transactional
    public NovelAudit handle(Long auditId, String result, Long auditorId, String remark) {
        if (!"PASS".equals(result) && !"TAKEDOWN".equals(result) && !"REJECT".equals(result)) {
            throw new BusinessException("非法处理结果");
        }
        NovelAudit audit = auditMapper.selectById(auditId);
        if (audit == null) {
            throw new BusinessException("审核记录不存在");
        }
        // 条件更新：仅当仍为 PENDING 时更新；影响 0 行 = 并发下已被处理
        int updated = auditMapper.update(null, new LambdaUpdateWrapper<NovelAudit>()
                .eq(NovelAudit::getId, auditId)
                .eq(NovelAudit::getResult, "PENDING")
                .set(NovelAudit::getResult, result)
                .set(NovelAudit::getAuditorId, auditorId)
                .set(NovelAudit::getAuditTime, LocalDateTime.now())
                .set(NovelAudit::getRemark, remark));
        if (updated == 0) {
            throw new BusinessException("该记录已被处理（请刷新列表）");
        }
        audit.setResult(result);
        audit.setAuditorId(auditorId);
        audit.setAuditTime(LocalDateTime.now());
        audit.setRemark(remark);
        return audit;
    }

    /** 某小说最近一次审核记录 */
    public NovelAudit latestByNovel(Long novelId) {
        return auditMapper.selectOne(new LambdaQueryWrapper<NovelAudit>()
                .eq(NovelAudit::getNovelId, novelId)
                .orderByDesc(NovelAudit::getCreateTime)
                .last("LIMIT 1"));
    }
}
