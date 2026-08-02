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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 测试基类：提供注册/登录/请求辅助方法。
 * 每个测试类使用独立用户名（带类名前缀），互不依赖、可重复运行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class TestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper om;

    @Autowired
    protected com.japy.mapper.UserMapper userMapper;

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /** 注册，若已存在则登录（幂等，支持重复运行） */
    protected String registerOrLogin(String username, String password, String nickname) throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"nickname\":\"" + nickname + "\"}"))
                .andReturn();
        JsonNode node = om.readTree(r.getResponse().getContentAsString());
        if (node.get("code").asInt() == 200 && node.get("data") != null && node.get("data").has("token")) {
            return node.get("data").get("token").asText();
        }
        return login(username, password);
    }

    protected String login(String username, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        JsonNode node = om.readTree(r.getResponse().getContentAsString());
        if (node.get("code").asInt() == 200 && node.get("data") != null && node.get("data").has("token")) {
            return node.get("data").get("token").asText();
        }
        return null;
    }

    protected JsonNode body(MvcResult result) throws Exception {
        // 必须显式 UTF-8：getContentAsString() 默认按 ISO-8859-1 解码，中文会乱码
        return om.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 注册管理端专用账号并提升为 admin（幂等） */
    protected String adminToken() throws Exception {
        String username = "t_admin_master";
        String password = "admin123";
        String token = registerOrLogin(username, password, "测试管理员");
        var admin = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.japy.entity.User>()
                        .eq(com.japy.entity.User::getUsername, username));
        if (admin != null && !"admin".equals(admin.getRole())) {
            admin.setRole("admin");
            userMapper.updateById(admin);
            token = login(username, password);
        }
        return token;
    }

    protected MvcResult postJson(String path, String token, String jsonBody) throws Exception {
        var req = post(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) req.header("Authorization", bearer(token));
        if (jsonBody != null) req.content(jsonBody);
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult putJson(String path, String token, String jsonBody) throws Exception {
        var req = put(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) req.header("Authorization", bearer(token));
        if (jsonBody != null) req.content(jsonBody);
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult deleteReq(String path, String token) throws Exception {
        var req = delete(path);
        if (token != null) req.header("Authorization", bearer(token));
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult getReq(String path, String token) throws Exception {
        var req = get(path);
        if (token != null) req.header("Authorization", bearer(token));
        return mockMvc.perform(req).andReturn();
    }

    protected String jsonBody(Map<String, Object> map) throws Exception {
        return om.writeValueAsString(map);
    }
}
