package com.recloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 分布式限流集成测试
 * <p>
 * 验证 @RateLimiter 注解 + Redis Lua 脚本的固定窗口限流逻辑：
 * - 连续请求超过阈值 → 返回 429
 * - 等待窗口过期 → 恢复正常
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimiterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Test
    void testLoginRateLimit() throws Exception {
        // 登录接口限流：5次/300秒
        // 清除限流计数，确保从干净状态开始
        if (redisTemplate != null) {
            redisTemplate.delete("rate_limit:ip:127.0.0.1:login");
        }
        String body = objectMapper.writeValueAsString(
                Map.of("username", "ratelimit_test_user", "password", "wrongpwd"));

        // 前5次到达业务逻辑（密码错误返回400，但未被限流）
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        // 第6次触发限流（429）
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void testRegisterRateLimit() throws Exception {
        // 注册接口限流：3次/60秒
        // 清除 IP 维度的限流计数，确保从干净状态开始
        if (redisTemplate != null) {
            redisTemplate.delete("rate_limit:ip:127.0.0.1:register");
        }

        // 用户名需 ≤20 位且每次不同（避免重复用户名干扰），密码满足强密码策略
        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        // 前3次用不同用户名注册成功（限流阈值 3/60s）
        for (int i = 0; i < 3; i++) {
            String body = objectMapper.writeValueAsString(Map.of(
                    "username", "rl" + i + suffix,
                    "nickname", "限流测试",
                    "password", "Test1234"));
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        // 第4次触发限流（429）
        String body4 = objectMapper.writeValueAsString(Map.of(
                "username", "rl3" + suffix,
                "nickname", "限流测试",
                "password", "Test1234"));
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body4))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void testRateLimitRecoveryAfterWindowExpires() throws Exception {
        // 验证限流窗口过期后恢复正常
        // 手动清除限流 key 模拟窗口过期
        if (redisTemplate != null) {
            redisTemplate.delete("rate_limit:ip:127.0.0.1:login");
        }

        String body = objectMapper.writeValueAsString(
                Map.of("username", "recovery_test", "password", "wrongpwd"));

        // 窗口过期后请求未被限流，到达业务逻辑（密码错误返回400，而非429）
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
