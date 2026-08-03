package com.japy.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.japy.base.AbstractIntegrationTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * RBAC 权限 + 防护机制测试：
 * 权限拦截（401/403）/ 幂等防重 / 登录限流 / XSS 双路径防护 / 密码不外泄。
 * @Transactional：测试数据回滚，不污染测试库。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class RbacGuardTest extends AbstractIntegrationTest {

    @Test
    @Order(1)
    void 普通用户无权限访问管理接口() throws Exception {
        // 注册普通用户
        JsonNode reg = postJson("/auth/register",
                Map.of("username", "t_rbac_" + nextTs(), "password", "123456", "nickname", "权限测试"), null);
        String userToken = reg.get("data").get("accessToken").asText();

        // 普通用户访问用户管理 → 403
        JsonNode denied = getJson("/system/user/list?page=1&size=10", userToken);
        assertEquals(403, denied.get("code").asInt(), denied.toString());

        // 无 token → 401
        JsonNode anon = getJson("/system/user/list?page=1&size=10", null);
        assertEquals(401, anon.get("code").asInt());
    }

    @Test
    @Order(2)
    void 管理员访问正常且幂等防重复() throws Exception {
        String adminToken = loginAsAdmin();
        JsonNode list = getJson("/system/user/list?page=1&size=10", adminToken);
        assertEquals(200, list.get("code").asInt());

        // 幂等：10 秒内相同参数重复创建用户 → 第二次被拒
        String body = om.writeValueAsString(Map.of(
                "username", "t_idem_" + nextTs(), "password", "123456", "nickname", "幂等测试"));
        JsonNode first = requestPost("/system/user", body, adminToken);
        assertEquals(200, first.get("code").asInt(), first.toString());
        JsonNode second = requestPost("/system/user", body, adminToken);
        assertEquals(400, second.get("code").asInt(), "重复提交应被幂等拦截");
        assertTrue(second.get("msg").asText().contains("重复"), second.toString());
    }

    @Test
    @Order(3)
    void XSS防护() throws Exception {
        // 1) body 校验：注册昵称含脚本 → 拒绝（长度须在 20 字内，确保命中 XSS 而非长度校验）
        JsonNode reg = postJson("/auth/register",
                Map.of("username", "t_xss_" + nextTs(), "password", "123456",
                        "nickname", "<script>x</script>"), null);
        assertEquals(400, reg.get("code").asInt(), reg.toString());
        assertTrue(reg.get("msg").asText().contains("非法字符"), reg.toString());

        // 2) query 参数转义：危险片段被 XssFilter 转义，接口正常返回而非 500
        String adminToken = loginAsAdmin();
        JsonNode list = getJson("/system/loginlog/list?page=1&size=5&keyword=javascript%3Aalert(1)", adminToken);
        assertEquals(200, list.get("code").asInt(), "危险 query 参数应被转义而非导致异常");
    }

    @Test
    @Order(4)
    void 密码哈希不外泄() throws Exception {
        String adminToken = loginAsAdmin();
        JsonNode list = getJson("/system/user/list?page=1&size=10", adminToken);
        assertEquals(200, list.get("code").asInt());
        JsonNode first = list.get("data").get("list").get(0);
        assertFalse(first.has("password"), "用户列表接口不得返回密码字段");
    }

    @Test
    @Order(5)
    void 登录接口限流() throws Exception {
        // 限流 3 次/秒：快速连续登录应触发限流提示
        // 注意：本用例置后，避免打满 admin 限流影响其他用例
        Integer last = null;
        for (int i = 0; i < 6; i++) {
            JsonNode node = postJson("/auth/login", Map.of("username", "admin", "password", "admin123"), null);
            last = node.get("code").asInt();
            if (last != 200) break;
        }
        assertNotEquals(200, last, "快速连续登录应触发限流");
    }

    private JsonNode requestPost(String path, String body, String token) throws Exception {
        var req = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) req.header("Authorization", "Bearer " + token);
        return om.readTree(new String(mockMvc.perform(req).andReturn().getResponse().getContentAsByteArray(),
                java.nio.charset.StandardCharsets.UTF_8));
    }
}
