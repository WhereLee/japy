package com.japy.novel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.base.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小说生命周期集成测试：上传(自动上架) → 状态流转 → 删除 → 用户端不可见。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class NovelLifecycleTest extends AbstractIntegrationTest {

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
    void 上传自动上架入库() throws Exception {
        String txt = "第一章 初入江湖\n武林风云起。\n\n少年提剑出门。\n\n第二章 遇险\n深山遇恶虎。\n\n第三章 拜师\n得遇高人指点。";
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain",
                txt.getBytes(StandardCharsets.UTF_8));

        JsonNode r = call(multipart("/admin/novel/upload")
                .file(file)
                .param("title", "测试剑客录")
                .param("author", "测试作者")
                .param("category", "武侠")
                .param("intro", "一个少年侠客的成长故事")
                .header("Authorization", "Bearer " + token));
        assertEquals(200, r.get("code").asInt(), r.toString());
        JsonNode data = r.get("data");
        assertEquals(0, data.get("status").asInt(), "上传后应自动上架（连载）");
        assertEquals(3, data.get("chapterCount").asInt(), "3 章");
        assertTrue(data.get("totalChars").asLong() > 0);
        assertTrue(data.get("id").asLong() > 0);
    }

    @Test
    @Order(2)
    void 状态流转() throws Exception {
        // 用 seed 小说 id=1
        JsonNode r = call(put("/admin/novel/1/status?status=3")
                .header("Authorization", "Bearer " + token));
        assertEquals(200, r.get("code").asInt());

        // 下架后用户端不可见
        JsonNode detail = call(get("/novel/1").header("Authorization", "Bearer " + token));
        assertEquals(400, detail.get("code").asInt(), "下架小说用户端不可见");

        // 恢复连载
        call(put("/admin/novel/1/status?status=0").header("Authorization", "Bearer " + token));
        JsonNode detail2 = call(get("/novel/1").header("Authorization", "Bearer " + token));
        assertEquals(200, detail2.get("code").asInt(), "恢复连载后可见");
    }

    @Test
    @Order(3)
    void 完结状态() throws Exception {
        JsonNode r = call(put("/admin/novel/1/status?status=1")
                .header("Authorization", "Bearer " + token));
        assertEquals(200, r.get("code").asInt());
        JsonNode detail = call(get("/novel/1").header("Authorization", "Bearer " + token));
        assertEquals(200, detail.get("code").asInt(), "完结小说用户端可见");
    }

    @Test
    @Order(4)
    void 逻辑删除() throws Exception {
        JsonNode r = call(delete("/admin/novel/1").header("Authorization", "Bearer " + token));
        assertEquals(200, r.get("code").asInt());
        // 删除后用户端不可见 + 管理端列表不含
        JsonNode detail = call(get("/novel/1").header("Authorization", "Bearer " + token));
        assertEquals(400, detail.get("code").asInt(), "删除后用户端不可见");
        JsonNode list = call(get("/admin/novel/list?page=1&size=50")
                .header("Authorization", "Bearer " + token));
        boolean found = false;
        for (JsonNode n : list.get("data").get("list")) {
            if (n.get("id").asLong() == 1) found = true;
        }
        assertFalse(found, "管理端列表不应含逻辑删除的小说");
    }
}
