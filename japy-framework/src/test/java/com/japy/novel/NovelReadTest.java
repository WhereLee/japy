package com.japy.novel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.base.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小说阅读模块集成测试：
 * 列表 / 详情 / 章节列表 / 章节内容（上下章）/ 阅读进度 upsert。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NovelReadTest extends AbstractIntegrationTest {

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

    private JsonNode get(String url) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header("Authorization", "Bearer " + token));
    }

    private JsonNode post(String url, Object body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)));
    }

    private JsonNode put(String url, Object body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)));
    }

    @BeforeEach
    void loginOnce() throws Exception {
        if (token == null) {
            JsonNode r = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"admin123\"}"));
            assertEquals(200, r.get("code").asInt());
            token = r.get("data").get("accessToken").asText();
        }
    }

    @Test
    @Order(1)
    void 小说列表与详情() throws Exception {
        JsonNode list = get("/novel/list?page=1&size=10");
        assertEquals(200, list.get("code").asInt());
        assertTrue(list.get("data").get("total").asInt() >= 1, "seed 小说应存在");
        long novelId = list.get("data").get("list").get(0).get("id").asLong();

        JsonNode detail = get("/novel/" + novelId);
        assertEquals(200, detail.get("code").asInt());
        assertTrue(detail.get("data").get("chapterCount").asInt() >= 1);
        assertTrue(detail.get("data").get("totalChars").asLong() > 0, "总字数应为正");
    }

    @Test
    @Order(2)
    void 章节列表分页() throws Exception {
        JsonNode list = get("/novel/1/chapters?page=1&size=2");
        assertEquals(200, list.get("code").asInt());
        assertEquals(2, list.get("data").get("list").size(), "每页 2 条");
        assertTrue(list.get("data").get("total").asInt() >= 5, "seed 5 章");
        // 按章节号升序
        int no0 = list.get("data").get("list").get(0).get("chapterNo").asInt();
        int no1 = list.get("data").get("list").get(1).get("chapterNo").asInt();
        assertTrue(no0 < no1, "章节号应升序");
    }

    @Test
    @Order(3)
    void 章节内容与上下章() throws Exception {
        // 取第 2 章
        JsonNode chapters = get("/novel/1/chapters?page=1&size=50");
        long ch1 = chapters.get("data").get("list").get(0).get("id").asLong();
        long ch2 = chapters.get("data").get("list").get(1).get("id").asLong();

        JsonNode c2 = get("/novel/chapter/" + ch2);
        assertEquals(200, c2.get("code").asInt());
        JsonNode data = c2.get("data");
        assertEquals(ch1, data.get("prevChapterId").asLong(), "第2章上一章应为第1章");
        assertTrue(data.get("nextChapterId").asLong() > 0, "第2章应有下一章");
        assertTrue(data.get("paragraphs").size() >= 3, "每章至少 3 段");
        assertTrue(data.get("paragraphs").get(0).asText().length() > 10, "段落应有内容");

        // 第 1 章无上一章；最后一章无下一章
        JsonNode c1 = get("/novel/chapter/" + ch1);
        assertTrue(c1.get("data").get("prevChapterId").isNull(), "第1章无上一章");
        JsonNode last = get("/novel/chapter/" + chapters.get("data").get("list").get(4).get("id").asLong());
        assertTrue(last.get("data").get("nextChapterId").isNull(), "末章无下一章");
    }

    @Test
    @Order(4)
    void 阅读进度保存与读取() throws Exception {
        JsonNode chapters = get("/novel/1/chapters?page=1&size=50");
        long ch2 = chapters.get("data").get("list").get(1).get("id").asLong();

        Map<String, Object> body = new HashMap<>();
        body.put("chapterId", ch2);
        body.put("charOffset", 128);
        body.put("percent", 42.5);
        JsonNode saved = put("/novel/1/progress", body);
        assertEquals(200, saved.get("code").asInt());

        JsonNode prog = get("/novel/1/progress");
        assertEquals(200, prog.get("code").asInt());
        JsonNode data = prog.get("data");
        assertNotNull(data, "进度应存在");
        assertEquals(ch2, data.get("chapterId").asLong());
        assertEquals(128, data.get("charOffset").asInt());
        assertEquals(0, data.get("percent").decimalValue().compareTo(new java.math.BigDecimal("42.5")));

        // 覆盖更新（upsert）
        body.put("charOffset", 300);
        put("/novel/1/progress", body);
        JsonNode prog2 = get("/novel/1/progress");
        assertEquals(300, prog2.get("data").get("charOffset").asInt(), "进度应被覆盖");
    }
}
