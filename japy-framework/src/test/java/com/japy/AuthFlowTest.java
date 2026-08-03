package com.japy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 认证链路集成测试：注册/登录/刷新轮换/登出/失败锁定。
 * 依赖本机 Redis/RocketMQ/PostgreSQL（与 dev 环境一致）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    private static final String TS = String.valueOf(System.currentTimeMillis() % 1000000);

    private JsonNode postJson(String path, Object body, String token) throws Exception {
        var req = post(path).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body));
        if (token != null) req.header("Authorization", "Bearer " + token);
        MvcResult r = mockMvc.perform(req).andReturn();
        return om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode getJson(String path, String token) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
        if (token != null) req.header("Authorization", "Bearer " + token);
        MvcResult r = mockMvc.perform(req).andReturn();
        return om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @Order(1)
    void 注册登录刷新轮换登出() throws Exception {
        // 注册（注册即登录）
        JsonNode reg = postJson("/auth/register",
                Map.of("username", "t_auth_" + TS, "password", "123456", "nickname", "认证测试"), null);
        assertEquals(200, reg.get("code").asInt(), reg.toString());
        String access = reg.get("data").get("accessToken").asText();
        String refresh = reg.get("data").get("refreshToken").asText();
        assertFalse(access.isEmpty());
        assertTrue(reg.get("data").get("avatar").asText().startsWith("data:image/svg+xml"),
                "注册应生成 SVG 初始头像");

        // 携带 access token 访问个人信息（GET）
        JsonNode profile = getJson("/profile", access);
        assertEquals(200, profile.get("code").asInt(), profile.toString());

        // 刷新：refresh 轮换后旧 refresh 立即失效（防重放）
        JsonNode rf1 = postJson("/auth/refresh", Map.of("refreshToken", refresh), null);
        assertEquals(200, rf1.get("code").asInt(), rf1.toString());
        String newRefresh = rf1.get("data").get("refreshToken").asText();
        // 校验 Redis 会话中的 refresh 已被轮换（jti 保证每次唯一）
        Long uid = rf1.get("data").get("userId").asLong();
        String savedInRedis = redis.opsForValue().get("login:refresh:" + uid);
        assertEquals(newRefresh, savedInRedis, "刷新后 Redis 会话中的 refresh 应被轮换");
        JsonNode rf2 = postJson("/auth/refresh", Map.of("refreshToken", refresh), null);
        assertEquals(400, rf2.get("code").asInt(), "旧 refresh 应失效（轮换）");
        assertNotEquals(refresh, newRefresh, "refresh 应轮换为新值");

        // 登出后会话失效
        assertEquals(200, postJson("/auth/logout", Map.of(), rf1.get("data").get("accessToken").asText()).get("code").asInt());
        JsonNode after = postJson("/profile", Map.of(), rf1.get("data").get("accessToken").asText());
        assertNotEquals(200, after.get("code").asInt(), "登出后 access token 应失效");
    }

    @Test
    @Order(2)
    void 登录失败锁定() throws Exception {
        // 连续失败超过阈值（默认 5 次）→ 锁定提示（每次间隔 400ms 规避登录限流 3/s）
        int maxFail = 5;
        Integer lastCode = null;
        for (int i = 0; i < maxFail + 1; i++) {
            JsonNode r = postJson("/auth/login",
                    Map.of("username", "t_lock_" + TS, "password", "wrong-pwd"), null);
            lastCode = r.get("code").asInt();
            Thread.sleep(400);
        }
        assertEquals(400, lastCode, "连续失败应触发锁定");
        // 锁定提示包含"锁定"
        JsonNode locked = postJson("/auth/login",
                Map.of("username", "t_lock_" + TS, "password", "wrong-pwd"), null);
        assertTrue(locked.get("msg").asText().contains("锁定"), locked.toString());
    }
}
