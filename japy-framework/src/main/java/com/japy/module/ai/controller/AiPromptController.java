package com.japy.module.ai.controller;

import com.japy.common.R;
import com.japy.module.ai.entity.AiPrompt;
import com.japy.module.ai.service.AiPromptService;
import com.japy.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * LLM 提示词管理（技术管理端）。
 * 管理每个 LLM 场景的固定 system prompt（不含检索临时塞入的文档）：
 * 查看 / 编辑（升版本，立即生效）/ 回滚（版本化）。
 */
@RestController
@RequestMapping("/ai/prompt")
@RequiredArgsConstructor
public class AiPromptController {

    private final AiPromptService promptService;

    /** 全部场景 + 当前生效版本 */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('ai:prompt:list')")
    public R<List<AiPrompt>> list() {
        return R.ok(promptService.listAll());
    }

    /** 某场景全部版本（新→旧） */
    @GetMapping("/{code}/versions")
    @PreAuthorize("@ss.hasPermi('ai:prompt:list')")
    public R<List<AiPrompt>> versions(@PathVariable String code) {
        return R.ok(promptService.versions(code));
    }

    /** 编辑保存：升版本 + 立即生效 */
    @PutMapping("/{code}")
    @PreAuthorize("@ss.hasPermi('ai:prompt:edit')")
    public R<AiPrompt> update(@PathVariable String code, @RequestBody Map<String, String> body,
                              @AuthenticationPrincipal LoginUser user) {
        String prompt = body.get("systemPrompt");
        if (prompt == null || prompt.isBlank()) {
            return R.fail("systemPrompt 不能为空");
        }
        return R.ok(promptService.update(code, prompt, user == null ? null : user.getUserId()));
    }

    /** 回滚到指定版本 + 立即生效 */
    @PostMapping("/{code}/rollback/{version}")
    @PreAuthorize("@ss.hasPermi('ai:prompt:rollback')")
    public R<AiPrompt> rollback(@PathVariable String code, @PathVariable int version,
                                @AuthenticationPrincipal LoginUser user) {
        return R.ok(promptService.rollback(code, version, user == null ? null : user.getUserId()));
    }
}
