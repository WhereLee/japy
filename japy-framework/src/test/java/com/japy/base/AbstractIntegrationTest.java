package com.japy.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 集成测试基类：
 * - 独立测试库 japy_moments_test（Flyway 自动建表 + 预置数据，不污染开发库）
 * - MockMvc 全链路请求 + JSON 工具方法（UTF-8 安全解码）
 * - 时间戳序列生成器（保证测试数据唯一，支持并发/多轮运行）
 *
 * 依赖本机中间件：PostgreSQL / Redis / RocketMQ（与 dev 一致，纯集成验证）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper om;
    @Autowired
    protected StringRedisTemplate redis;

    /** 测试数据唯一性：时间戳 + 自增序号 */
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    protected static String nextTs() {
        return String.valueOf(SEQ.incrementAndGet());
    }

    /** 测试库不存在时自动创建（幂等）：先直连 postgres 库建库，Flyway 随后建表 */
    @BeforeAll
    static void ensureTestDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/postgres", "postgres", "root");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE japy_moments_test");
        } catch (SQLException e) {
            // 已存在则忽略（SQLState 42P04: duplicate_database）
            if (!"42P04".equals(e.getSQLState())) {
                throw e;
            }
        }
    }

    // ---------- 请求工具 ----------

    protected JsonNode postJson(String path, Object body, String token) throws Exception {
        return request(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)), token);
    }

    protected JsonNode getJson(String path, String token) throws Exception {
        return request(get(path), token);
    }

    private JsonNode request(MockHttpServletRequestBuilder builder, String token) throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        // UTF-8 显式解码：平台默认编码（如 GBK）会导致中文断言失败
        return om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 管理员登录（Flyway 预置账号 admin/admin123），返回 access token */
    protected String loginAsAdmin() throws Exception {
        JsonNode node = postJson("/auth/login", Map.of("username", "admin", "password", "admin123"), null);
        return node.get("data").get("accessToken").asText();
    }
}
