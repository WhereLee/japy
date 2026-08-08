package com.japy.module.rag.controller;

import com.japy.common.R;
import com.japy.module.rag.RagClient;
import com.japy.module.rag.RagUnavailableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * RAG 问答接口：
 * - POST /rag/ask           登录用户对小说提问
 * - POST /admin/rag/sync    管理端触发索引同步（admin）
 * - GET  /admin/rag/status  管理端查看索引状态（admin）
 * - GET  /admin/rag/health  RAG 服务探活
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RagController {

    private final RagClient ragClient;

    /** 用户端问答 */
    @PostMapping("/rag/ask")
    public R<Map<String, Object>> ask(@Valid @RequestBody AskDTO dto) {
        try {
            var answer = ragClient.ask(dto.getNovelId(), dto.getQuestion());
            return R.ok(Map.of(
                    "answer", answer.getAnswer(),
                    "sources", answer.getSources(),
                    "meta", answer.getMeta()));
        } catch (RagUnavailableException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 管理端：同步索引（novelId 为空则全量） */
    @PostMapping("/admin/rag/sync")
    @PreAuthorize("@ss.hasPermi('rag:sync')")
    @com.japy.aspect.OperLog(title = "RAG 索引", businessType = 2)
    public R<Map<String, Object>> sync(@RequestBody(required = false) Map<String, Long> body) {
        try {
            Long novelId = body == null ? null : body.get("novel_id");
            return R.ok(ragClient.sync(novelId));
        } catch (RagUnavailableException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 管理端：索引状态 */
    @GetMapping("/admin/rag/status")
    @PreAuthorize("@ss.hasPermi('rag:list')")
    public R<Map<String, Object>> status(@RequestParam(required = false) Long novelId) {
        try {
            return R.ok(ragClient.status(novelId));
        } catch (RagUnavailableException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 管理端：同步任务进度（异步切块/入库进度 + 分阶段耗时） */
    @GetMapping("/admin/rag/sync/status")
    @PreAuthorize("@ss.hasPermi('rag:list')")
    public R<Map<String, Object>> syncStatus(@RequestParam(required = false) Long novelId) {
        try {
            return R.ok(ragClient.syncStatus(novelId));
        } catch (RagUnavailableException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 探活（管理端展示 RAG 服务状态） */
    @GetMapping("/admin/rag/health")
    @PreAuthorize("@ss.hasPermi('rag:list')")
    public R<Boolean> health() {
        return R.ok(ragClient.available());
    }

    @Data
    public static class AskDTO {
        @NotNull(message = "小说不能为空")
        private Long novelId;
        @NotBlank(message = "问题不能为空")
        private String question;
    }
}
