package com.japy.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.japy.base.AbstractIntegrationTest;
import com.japy.module.ai.entity.AiPrompt;
import com.japy.module.ai.mapper.AiPromptMapper;
import com.japy.module.ai.service.AiPromptService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM 提示词注册表集成测试（技术管理端）。
 * 覆盖契约：
 *  - list：tech 可见全部 3 场景当前生效版本
 *  - update：升版本 + 旧版本退役 + getContent 立即返回新内容（无需重启，走真实提交验证 afterCommit 缓存刷新）
 *  - rollback：回滚到历史版本 + getContent 立即生效
 *  - 版本号基于最大版本（回滚后再编辑不撞唯一约束）
 *  - 校验：空 systemPrompt / 非法 code / 回滚不存在版本
 *  - 权限：无 ai:prompt 权限的角色访问被拒（403）
 * 注意：真实提交到测试库（验证 afterCommit 缓存刷新路径）；断言全部用相对版本号
 * （测试库多次运行版本累积，不依赖固定版本号）。测试库每次 Spring 启动由 Flyway 重建。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiPromptTest extends AbstractIntegrationTest {

    @Autowired
    private AiPromptMapper promptMapper;
    @Autowired
    private AiPromptService promptService;

    /** 某 code 当前最大版本号（含历史），用于相对断言 */
    private int maxVersion(String code) {
        List<AiPrompt> versions = promptService.versions(code);
        return versions.isEmpty() ? 0 : versions.get(0).getVersion();
    }

    // ---------- 1. list 契约 ----------

    @Test
    @Order(1)
    void techCanListAllActivePrompts() throws Exception {
        String token = loginAsTech();
        JsonNode node = getJson("/ai/prompt/list", token);
        assertEquals(200, node.get("code").asInt());
        JsonNode data = node.get("data");
        assertEquals(3, data.size(), "应有 3 个 LLM 场景");
        assertTrue(node.toString().contains("novel_qa"));
        assertTrue(node.toString().contains("ops_interpret"));
        assertTrue(node.toString().contains("feedback_analysis"));
        // 每个场景都带当前生效版本与完整 prompt 文本
        for (JsonNode p : data) {
            assertTrue(p.get("version").asInt() >= 1);
            assertTrue(p.get("status").asInt() == 1);
            assertFalse(p.get("systemPrompt").asText().isBlank());
        }
    }

    // ---------- 2. update 契约：升版本 + 立即生效（真实提交 → afterCommit 刷新缓存） ----------

    @Test
    @Order(2)
    void updateRaisesVersionAndTakesEffectImmediately() throws Exception {
        String token = loginAsTech();
        String code = "ops_interpret";
        String original = promptService.getContent(code);
        int maxV = maxVersion(code);

        JsonNode node = putJson("/ai/prompt/" + code,
                Map.of("systemPrompt", original + "\n【测试】新增一条补充规则。"), token);
        assertEquals(200, node.get("code").asInt());
        assertEquals(maxV + 1, node.get("data").get("version").asInt(), "保存必须升版本");

        // 立即生效：getContent 返回新内容（真实提交后 afterCommit 已刷新缓存，无需重启）
        assertTrue(promptService.getContent(code).contains("【测试】新增一条补充规则"));
        // 旧版本退役：更新前生效版本 status=0
        AiPrompt oldActive = promptService.versions(code).stream()
                .filter(v -> v.getVersion() == maxV).findFirst().orElseThrow();
        assertEquals(0, oldActive.getStatus(), "旧版本必须退役(status=0)");
    }

    // ---------- 3. rollback 契约：回滚 + 立即生效 ----------

    @Test
    @Order(3)
    void rollbackRestoresTargetVersionImmediately() throws Exception {
        String token = loginAsTech();
        String code = "feedback_analysis";
        String original = promptService.getContent(code);
        int maxV = maxVersion(code);

        // 先升到 maxV+1
        putJson("/ai/prompt/" + code, Map.of("systemPrompt", original + "\n【v2】改动"), token);
        assertEquals(maxV + 1, maxVersion(code));
        assertTrue(promptService.getContent(code).contains("【v2】改动"));

        // 回滚到 v1（初始版本始终存在）
        JsonNode node = postJson("/ai/prompt/" + code + "/rollback/1", null, token);
        assertEquals(200, node.get("code").asInt());
        assertEquals(1, node.get("data").get("version").asInt());

        // 立即生效：恢复 v1 内容，maxV+1 退役
        assertFalse(promptService.getContent(code).contains("【v2】改动"));
        assertEquals(0, promptService.versions(code).stream()
                .filter(v -> v.getVersion() == maxV + 1).findFirst().orElseThrow().getStatus());
    }

    @Test
    @Order(4)
    void editAfterRollbackUsesMaxVersionNotActiveVersion() throws Exception {
        // 回归契约：回滚后再编辑，版本号必须基于该 code 的最大版本（而非当前生效版本），
        // 否则会与历史版本撞唯一约束 (code, version) —— 曾导致"保存失败：服务器内部错误"
        String token = loginAsTech();
        String code = "ops_interpret";
        String original = promptService.getContent(code);
        int maxV = maxVersion(code);

        // 编辑成 maxV+1 → 回滚到 v1
        putJson("/ai/prompt/" + code, Map.of("systemPrompt", original + "\n【v2】"), token);
        postJson("/ai/prompt/" + code + "/rollback/1", null, token);

        // 回滚后再编辑：必须成功且版本为 maxV+2（而非撞已存在的 maxV+1）
        JsonNode node = putJson("/ai/prompt/" + code,
                Map.of("systemPrompt", original + "\n【v3】回滚后再编辑"), token);
        assertEquals(200, node.get("code").asInt(), "回滚后再编辑必须成功（不得撞唯一约束）");
        assertEquals(maxV + 2, node.get("data").get("version").asInt());
        assertTrue(promptService.getContent(code).contains("【v3】回滚后再编辑"));
    }

    // ---------- 4. 校验契约 ----------

    @Test
    @Order(5)
    void updateRejectsBlankPrompt() throws Exception {
        String token = loginAsTech();
        JsonNode node = putJson("/ai/prompt/novel_qa", Map.of("systemPrompt", "  "), token);
        assertEquals(400, node.get("code").asInt(), "空提示词必须拒绝");
    }

    @Test
    @Order(6)
    void updateRejectsIllegalCode() throws Exception {
        String token = loginAsTech();
        // 非法 code：含非法字符 / 超长，必须 400（防借 update 创建任意场景）
        JsonNode node1 = putJson("/ai/prompt/evil code!", Map.of("systemPrompt", "x"), token);
        assertEquals(400, node1.get("code").asInt(), "非法字符 code 必须拒绝");
        String longCode = "a".repeat(60);
        JsonNode node2 = putJson("/ai/prompt/" + longCode, Map.of("systemPrompt", "x"), token);
        assertEquals(400, node2.get("code").asInt(), "超长 code 必须拒绝");
    }

    @Test
    @Order(7)
    void rollbackUnknownVersionRejected() throws Exception {
        String token = loginAsTech();
        JsonNode node = postJson("/ai/prompt/novel_qa/rollback/999", null, token);
        assertEquals(400, node.get("code").asInt(), "回滚不存在的版本必须拒绝");
    }

    // ---------- 5. 权限契约 ----------

    @Test
    @Order(8)
    void userWithoutPromptPermForbidden() throws Exception {
        // 普通用户（无 ai:prompt 权限）访问 list 应 403：注册一个新用户（默认 user 角色）
        String uname = "npp" + System.currentTimeMillis(); // 用户名 ≤20 字符，跨运行唯一
        JsonNode reg = postJson("/auth/register",
                Map.of("username", uname, "password", "Passw0rd!", "nickname", "无权限用户"), null);
        assertEquals(200, reg.get("code").asInt(), "测试用户注册失败");
        String token = reg.get("data").get("accessToken").asText();

        JsonNode node = getJson("/ai/prompt/list", token);
        assertEquals(403, node.get("code").asInt(), "无权限访问必须 403");
    }

    // ---------- 工具 ----------

    private JsonNode putJson(String path, Object body, String token) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        var result = mockMvc.perform(req).andReturn();
        return om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String loginAsTech() throws Exception {
        Thread.sleep(500); // 避开限流
        JsonNode node = postJson("/auth/login", Map.of("username", "tech", "password", "tech123456"), null);
        return node.get("data").get("accessToken").asText();
    }
}
