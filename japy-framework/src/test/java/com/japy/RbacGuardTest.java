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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * RBAC 权限 + 防护机制测试：权限拦截 / 幂等防重 / 登录限流。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RbacGuardTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;

    private static final String TS = String.valueOf(System.currentTimeMillis() % 1000000);

    private JsonNode req(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String login(String username, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn();
        JsonNode node = om.readTree(r.getResponse().getContentAsString());
        return node.get("data").get("accessToken").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        var req = get(path);
        if (token != null) req.header("Authorization", "Bearer " + token);
        return req(mockMvc.perform(req).andReturn());
    }

    @Test
    @Order(1)
    void 普通用户无权限访问管理接口() throws Exception {
        // 注册普通用户
        MvcResult reg = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "username", "t_rbac_" + TS, "password", "123456", "nickname", "权限测试"))))
                .andReturn();
        String userToken = req(reg).get("data").get("accessToken").asText();

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
        String adminToken = login("admin", "admin123");
        JsonNode list = getJson("/system/user/list?page=1&size=10", adminToken);
        assertEquals(200, list.get("code").asInt());

        // 幂等：10 秒内相同参数重复创建用户 → 第二次被拒
        String body = om.writeValueAsString(Map.of(
                "username", "t_idem_" + TS, "password", "123456", "nickname", "幂等测试"));
        JsonNode first = req(mockMvc.perform(post("/system/user")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn());
        assertEquals(200, first.get("code").asInt(), first.toString());
        JsonNode second = req(mockMvc.perform(post("/system/user")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn());
        assertEquals(400, second.get("code").asInt(), "重复提交应被幂等拦截");
        assertTrue(second.get("msg").asText().contains("重复"), second.toString());
    }

    @Test
    @Order(3)
    void 登录接口限流() throws Exception {
        // 限流 3 次/秒：快速连续登录应触发限流提示
        Integer last = null;
        for (int i = 0; i < 6; i++) {
            MvcResult r = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(Map.of("username", "admin", "password", "admin123"))))
                    .andReturn();
            JsonNode node = om.readTree(r.getResponse().getContentAsString());
            last = node.get("code").asInt();
            if (last != 200) break;
        }
        assertNotEquals(200, last, "快速连续登录应触发限流");
    }
}
