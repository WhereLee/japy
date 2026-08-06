package com.japy.audit;

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
 * 内容审核集成测试：上传含敏感词 → PENDING 留痕 → 处理（通过/下架联动）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class ContentAuditTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;

    private static String token;
    private static long auditId;

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
    void 上传含敏感词小说生成PENDING() throws Exception {
        String txt = "第一章 茶馆\n赌场里人声鼎沸，有人在讨论如何代理博彩业务，甚至提到加微信领红包的引流方式。\n\n第二章 平静\n茶馆二楼清净雅致，茶香四溢。";
        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain",
                txt.getBytes(StandardCharsets.UTF_8));
        JsonNode r = call(multipart("/admin/novel/upload")
                .file(file)
                .param("title", "测试违规书")
                .param("author", "测试")
                .param("category", "都市")
                .header("Authorization", "Bearer " + token));
        assertEquals(200, r.get("code").asInt());
        // 小说仍上架（扫描不自动下架）
        assertEquals(0, r.get("data").get("status").asInt(), "命中后小说保持上架");

        // 最新一条审核记录应为 PENDING
        JsonNode list = call(get("/audit/list?page=1&size=5&result=PENDING")
                .header("Authorization", "Bearer " + token));
        JsonNode first = list.get("data").get("list").get(0);
        assertEquals("PENDING", first.get("result").asText());
        assertTrue(first.get("ruleHits").asText().contains("博彩"), "命中词应含博彩");
        assertTrue(first.get("ruleHits").asText().contains("加微信领红包"));
        auditId = first.get("id").asLong();
    }

    @Test
    @Order(2)
    void 正常小说直接PASS() throws Exception {
        String txt = "第一章 清茶\n山间清泉流淌，茶香弥漫。";
        MockMultipartFile file = new MockMultipartFile("file", "good.txt", "text/plain",
                txt.getBytes(StandardCharsets.UTF_8));
        JsonNode r = call(multipart("/admin/novel/upload")
                .file(file)
                .param("title", "测试清茶")
                .param("author", "测试")
                .header("Authorization", "Bearer " + token));
        assertEquals(200, r.get("code").asInt());
        JsonNode list = call(get("/audit/list?page=1&size=5&result=PASS")
                .header("Authorization", "Bearer " + token));
        assertTrue(list.get("data").get("total").asInt() >= 1, "应有 PASS 记录");
    }

    @Test
    @Order(3)
    void 确认通过PASS() throws Exception {
        // 用 Order1 的 PENDING 记录（@Transactional 回滚，需重新造）
        String txt = "第一章\n这里涉及约炮字眼测试。";
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain",
                txt.getBytes(StandardCharsets.UTF_8));
        JsonNode up = call(multipart("/admin/novel/upload")
                .file(file).param("title", "测试通过书").param("author", "t")
                .header("Authorization", "Bearer " + token));
        long nid = up.get("data").get("id").asLong();
        JsonNode list = call(get("/audit/list?page=1&size=1&result=PENDING")
                .header("Authorization", "Bearer " + token));
        long aid = list.get("data").get("list").get(0).get("id").asLong();

        JsonNode r = call(post("/audit/" + aid + "/pass")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"remark\":\"人工确认合规\"}"));
        assertEquals(200, r.get("code").asInt());
        // 小说仍上架
        JsonNode detail = call(get("/novel/" + nid).header("Authorization", "Bearer " + token));
        assertEquals(200, detail.get("code").asInt());
    }

    @Test
    @Order(4)
    void 下架联动() throws Exception {
        String txt = "第一章\n宣传赌博危害大。";
        MockMultipartFile file = new MockMultipartFile("file", "y.txt", "text/plain",
                txt.getBytes(StandardCharsets.UTF_8));
        JsonNode up = call(multipart("/admin/novel/upload")
                .file(file).param("title", "测试下架书").param("author", "t")
                .header("Authorization", "Bearer " + token));
        long nid = up.get("data").get("id").asLong();
        JsonNode list = call(get("/audit/list?page=1&size=1&result=PENDING")
                .header("Authorization", "Bearer " + token));
        long aid = list.get("data").get("list").get(0).get("id").asLong();

        JsonNode r = call(post("/audit/" + aid + "/takedown")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"remark\":\"违规下架\"}"));
        assertEquals(200, r.get("code").asInt());
        // 联动后用户端不可见
        JsonNode detail = call(get("/novel/" + nid).header("Authorization", "Bearer " + token));
        assertEquals(400, detail.get("code").asInt(), "下架后用户端不可见");
    }

    @Test
    @Order(5)
    void 重新扫描() throws Exception {
        JsonNode r = call(post("/audit/1/rescan")
                .header("Authorization", "Bearer " + token));
        assertEquals(200, r.get("code").asInt());
        assertTrue(r.get("data").asInt() >= 0, "重扫返回命中数");
    }
}
