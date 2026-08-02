package com.japy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 认证模块测试：注册/登录的正常路径与全部边界。
 */
class AuthTest extends TestBase {

    private static final String PREFIX = "t_auth_";

    // ===== 注册正常路径 =====

    @Test @Order(1)
    void 注册成功返回token() throws Exception {
        String token = registerOrLogin(PREFIX + "ok", "pass123", "认证正常用户");
        assertNotNull(token, "注册成功应返回 token");
    }

    @Test @Order(2)
    void 注册成功后可用新密码登录() throws Exception {
        String username = PREFIX + "loginok";
        registerOrLogin(username, "pass123", "登录验证用户");
        String token = login(username, "pass123");
        assertNotNull(token, "注册后应能登录");
    }

    // ===== 注册边界 =====

    @Test @Order(10)
    void 重复用户名被拒() throws Exception {
        String username = PREFIX + "dup";
        registerOrLogin(username, "pass123", "重复用户1");
        MvcResult r = postJson("/auth/register", null,
                "{\"username\":\"" + username + "\",\"password\":\"pass123\",\"nickname\":\"重复用户2\"}");
        JsonNode node = body(r);
        assertEquals(400, node.get("code").asInt(), "重复用户名应被拒");
    }

    @Test @Order(11)
    void 空用户名被拒() throws Exception {
        MvcResult r = postJson("/auth/register", null,
                "{\"username\":\"\",\"password\":\"pass123\",\"nickname\":\"x\"}");
        assertEquals(400, body(r).get("code").asInt());
    }

    @Test @Order(12)
    void 缺失用户名被拒() throws Exception {
        MvcResult r = postJson("/auth/register", null,
                "{\"password\":\"pass123\",\"nickname\":\"x\"}");
        assertEquals(400, body(r).get("code").asInt());
    }

    @Test @Order(13)
    void 密码少于6位被拒() throws Exception {
        MvcResult r = postJson("/auth/register", null,
                "{\"username\":\"" + PREFIX + "shortpwd\",\"password\":\"12345\",\"nickname\":\"x\"}");
        assertEquals(400, body(r).get("code").asInt(), "密码不足6位应被拒");
    }

    @Test @Order(14)
    void 空昵称被拒() throws Exception {
        MvcResult r = postJson("/auth/register", null,
                "{\"username\":\"" + PREFIX + "nonick\",\"password\":\"pass123\",\"nickname\":\"\"}");
        assertEquals(400, body(r).get("code").asInt());
    }

    @Test @Order(15)
    void 用户名超50字符被拒() throws Exception {
        String longName = "u".repeat(51);
        MvcResult r = postJson("/auth/register", null,
                "{\"username\":\"" + longName + "\",\"password\":\"pass123\",\"nickname\":\"x\"}");
        assertEquals(400, body(r).get("code").asInt(), "超长用户名应被拒");
    }

    @Test @Order(16)
    void 昵称超50字符被拒() throws Exception {
        String longNick = "n".repeat(51);
        MvcResult r = postJson("/auth/register", null,
                "{\"username\":\"" + PREFIX + "longnick\",\"password\":\"pass123\",\"nickname\":\"" + longNick + "\"}");
        assertEquals(400, body(r).get("code").asInt(), "超长昵称应被拒");
    }

    @Test @Order(17)
    void 非法JSON返回友好400() throws Exception {
        MvcResult r = postJson("/auth/register", null, "{not valid json");
        assertEquals(400, body(r).get("code").asInt(), "非法JSON应返回400而非500");
    }

    // ===== 登录边界 =====

    @Test @Order(20)
    void 错误密码被拒() throws Exception {
        String username = PREFIX + "wrongpwd";
        registerOrLogin(username, "pass123", "错密用户");
        MvcResult r = postJson("/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"wrongpass\"}");
        assertEquals(400, body(r).get("code").asInt());
    }

    @Test @Order(21)
    void 不存在的用户被拒() throws Exception {
        MvcResult r = postJson("/auth/login", null,
                "{\"username\":\"" + PREFIX + "ghost\",\"password\":\"pass123\"}");
        assertEquals(400, body(r).get("code").asInt());
    }

    @Test @Order(22)
    void 缺失参数被拒() throws Exception {
        MvcResult r = postJson("/auth/login", null, "{\"username\":\"x\"}");
        assertEquals(400, body(r).get("code").asInt());
    }

    // ===== 权限基础 =====

    @Test @Order(30)
    void 无token发动态返回401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/moments")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"未登录动态\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(31)
    void 伪造token返回401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/moments")
                        .header("Authorization", "Bearer fake.token.here")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"伪造token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(32)
    void 无token可浏览时间线() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/moments?page=1&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @Order(33)
    void 无token可看公开主页() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
