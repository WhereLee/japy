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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 社区核心链路集成测试
 * 覆盖：注册/登录/权限/发帖/评论/点赞/删除归属
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommunityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    private static String tokenAlice;
    private static String tokenBob;
    private static Long postId;

    private String json(Object obj) throws Exception {
        return om.writeValueAsString(obj);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    // ========== 认证 ==========

    @Test @Order(1)
    void 注册Alice() throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_alice\",\"password\":\"123456\",\"nickname\":\"测试Alice\"}"))
                .andReturn();
        JsonNode node = body(r);
        // 可能已存在（重复跑测试），两种都接受
        if (node.get("code").asInt() == 200) {
            tokenAlice = node.get("data").get("token").asText();
        } else {
            // 已存在则登录
            loginAlice();
        }
        Assertions.assertNotNull(tokenAlice);
    }

    @Test @Order(2)
    void 注册Bob() throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_bob\",\"password\":\"654321\",\"nickname\":\"测试Bob\"}"))
                .andReturn();
        JsonNode node = body(r);
        if (node.get("code").asInt() == 200) {
            tokenBob = node.get("data").get("token").asText();
        } else {
            loginBob();
        }
        Assertions.assertNotNull(tokenBob);
    }

    @Test @Order(3)
    void 重复用户名被拒() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_alice\",\"password\":\"111111\",\"nickname\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test @Order(4)
    void 密码错误被拒() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_alice\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ========== 权限 ==========

    @Test @Order(10)
    void 无Token发帖返回401() throws Exception {
        mockMvc.perform(post("/api/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novelId\":1,\"content\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(11)
    void 无Token可浏览信息流() throws Exception {
        mockMvc.perform(get("/api/posts").param("novelId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 发帖 ==========

    @Test @Order(20)
    void Alice发帖成功() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + tokenAlice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novelId\":1,\"content\":\"集成测试帖子\",\"quoteText\":\"原文引用\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("测试Alice"))
                .andReturn();
        postId = body(r).get("data").get("id").asLong();
    }

    @Test @Order(21)
    void 空内容被拒() throws Exception {
        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + tokenAlice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novelId\":1,\"content\":\"\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ========== 评论 ==========

    @Test @Order(30)
    void Bob评论Alice的帖子() throws Exception {
        mockMvc.perform(post("/api/comments")
                .header("Authorization", "Bearer " + tokenBob)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postId\":" + postId + ",\"content\":\"测试评论\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("测试Bob"));
    }

    @Test @Order(31)
    void Alice回复Bob() throws Exception {
        mockMvc.perform(post("/api/comments")
                .header("Authorization", "Bearer " + tokenAlice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postId\":" + postId + ",\"content\":\"收到\",\"replyTo\":\"测试Bob\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.replyTo").value("测试Bob"));
    }

    // ========== 点赞 ==========

    @Test @Order(40)
    void Bob点赞() throws Exception {
        mockMvc.perform(post("/api/posts/" + postId + "/like")
                .header("Authorization", "Bearer " + tokenBob))
                .andExpect(jsonPath("$.data.liked").value(true));
    }

    @Test @Order(41)
    void Bob取消点赞() throws Exception {
        mockMvc.perform(post("/api/posts/" + postId + "/like")
                .header("Authorization", "Bearer " + tokenBob))
                .andExpect(jsonPath("$.data.liked").value(false));
    }

    // ========== 删除归属 ==========

    @Test @Order(50)
    void Bob不能删Alice的帖子() throws Exception {
        mockMvc.perform(delete("/api/posts/" + postId)
                .header("Authorization", "Bearer " + tokenBob))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test @Order(51)
    void Alice能删自己的帖子() throws Exception {
        mockMvc.perform(delete("/api/posts/" + postId)
                .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 辅助 ==========

    private void loginAlice() throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_alice\",\"password\":\"123456\"}"))
                .andReturn();
        tokenAlice = body(r).get("data").get("token").asText();
    }

    private void loginBob() throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_bob\",\"password\":\"654321\"}"))
                .andReturn();
        tokenBob = body(r).get("data").get("token").asText();
    }
}
