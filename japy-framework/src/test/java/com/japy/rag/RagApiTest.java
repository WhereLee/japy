package com.japy.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.base.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RAG 接口集成测试：
 * - /rag/ask 登录用户可调（RAG 服务未启动时降级提示，不 500）
 * - 权限：未登录 401
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class RagApiTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;

    private static String token;

    private JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        String body = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return om.readTree(body);
    }

    @BeforeEach
    void loginOnce() throws Exception {
        if (token == null) {
            JsonNode r = call(post("/auth/login")
                    .contentType("application/json")
                    .content("{\"username\":\"admin\",\"password\":\"admin123\"}"));
            token = r.get("data").get("accessToken").asText();
        }
    }

    @Test
    @Order(1)
    void 未登录访问被拒() throws Exception {
        mockMvc.perform(post("/rag/ask")
                        .contentType("application/json")
                        .content("{\"novelId\":1,\"question\":\"测试\"}"))
                .andExpect(status().is(401));
    }

    @Test
    @Order(2)
    void 问答接口参数校验() throws Exception {
        // 缺 question → 400
        JsonNode r = call(post("/rag/ask")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"novelId\":1}"));
        assertEquals(400, r.get("code").asInt(), "缺 question 应 400");
    }

    @Test
    @Order(3)
    void 问答接口降级处理() throws Exception {
        // RAG 服务可能未启动：应返回明确提示（code 200 可用 / 400 降级提示），HTTP 200 不 500
        JsonNode r = call(post("/rag/ask")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"novelId\":1,\"question\":\"晨星号收到了什么？\"}"));
        assertTrue(r.get("code").asInt() == 200 || r.get("code").asInt() == 400,
                "降级应返回明确提示而非异常, code=" + r.get("code"));
    }

    @Test
    @Order(4)
    void 管理端同步接口权限() throws Exception {
        // admin 有 rag:sync（通配）
        JsonNode r = call(post("/admin/rag/sync")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"novel_id\":1}"));
        assertTrue(r.get("code").asInt() == 200 || r.get("code").asInt() == 400,
                "admin 可调同步，code=" + r.get("code"));
    }
}
