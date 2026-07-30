package com.recloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recloud.dto.request.LoginRequest;
import com.recloud.dto.request.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 认证流程集成测试
 *
 * 测试完整认证链路：注册 → 登录 → 访问受保护接口 → 刷新Token → 登出 → 黑名单验证
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static String accessToken;
    private static String refreshToken;
    private static final String TEST_USERNAME = "tu_" + (System.currentTimeMillis() % 1000000000L);
    private static final String TEST_PASSWORD = "Test@1234";
    private static final String TEST_NICKNAME = "测试用户";

    @BeforeEach
    void clearRateLimits() {
        // 清除限流计数，避免测试间共享 Redis 限流状态相互污染
        redisTemplate.delete("rate_limit:ip:127.0.0.1:login");
        redisTemplate.delete("rate_limit:ip:127.0.0.1:register");
    }

    @Test
    @Order(1)
    void testRegister() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(TEST_USERNAME);
        request.setNickname(TEST_NICKNAME);
        request.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        var response = objectMapper.readTree(json);
        accessToken = response.get("data").get("accessToken").asText();
        refreshToken = response.get("data").get("refreshToken").asText();
    }

    @Test
    @Order(2)
    void testLogin() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        var response = objectMapper.readTree(json);
        accessToken = response.get("data").get("accessToken").asText();
        refreshToken = response.get("data").get("refreshToken").asText();
    }

    @Test
    @Order(3)
    void testAccessProtectedEndpointWithToken() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void testAccessProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void testRefreshToken() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    @Order(6)
    void testLogout() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Refresh-Token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(7)
    void testBlacklistedTokenCannotAccess() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    void testDuplicateRegisterFails() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(TEST_USERNAME);
        request.setNickname(TEST_NICKNAME);
        request.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @Order(9)
    void testLoginWithWrongPassword() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword("WrongPassword1");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }
}
