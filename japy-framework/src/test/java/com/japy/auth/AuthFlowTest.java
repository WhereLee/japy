package com.japy.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.japy.base.AbstractIntegrationTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证链路集成测试：注册/登录/刷新轮换/登出/失败锁定。
 * 覆盖：JWT 双 token、Redis 会话轮换（防重放）、SVG 头像生成、锁定策略。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowTest extends AbstractIntegrationTest {

    @Test
    @Order(1)
    void 注册登录刷新轮换登出() throws Exception {
        String ts = nextTs();
        // 注册（注册即登录，自动生成初始头像）
        JsonNode reg = postJson("/auth/register",
                Map.of("username", "t_auth_" + ts, "password", "123456", "nickname", "认证测试"), null);
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
        assertEquals(200, postJson("/auth/logout", Map.of(),
                rf1.get("data").get("accessToken").asText()).get("code").asInt());
        JsonNode after = postJson("/profile", Map.of(), rf1.get("data").get("accessToken").asText());
        assertNotEquals(200, after.get("code").asInt(), "登出后 access token 应失效");
    }

    @Test
    @Order(2)
    void 登录失败锁定() throws Exception {
        // 连续失败超过阈值（默认 5 次）→ 锁定提示（每次间隔 400ms 规避登录限流 3/s）
        String username = "t_lock_" + nextTs();
        int maxFail = 5;
        Integer lastCode = null;
        for (int i = 0; i < maxFail + 1; i++) {
            JsonNode r = postJson("/auth/login", Map.of("username", username, "password", "wrong-pwd"), null);
            lastCode = r.get("code").asInt();
            Thread.sleep(400);
        }
        assertEquals(400, lastCode, "连续失败应触发锁定");
        // 锁定提示包含"锁定"
        JsonNode locked = postJson("/auth/login", Map.of("username", username, "password", "wrong-pwd"), null);
        assertTrue(locked.get("msg").asText().contains("锁定"), locked.toString());
    }
}
