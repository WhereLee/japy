package com.japy;

import com.fasterxml.jackson.databind.JsonNode;
import com.japy.entity.Novel;
import com.japy.entity.NovelChapter;
import com.japy.entity.NovelParagraph;
import com.japy.mapper.NovelChapterMapper;
import com.japy.mapper.NovelMapper;
import com.japy.mapper.NovelParagraphMapper;
import com.japy.service.ChapterDetector;
import com.japy.service.ParagraphSplitter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 小说上传入库链路：上传 → 章节检测 → 统计 → 落盘目录 → 数据库。
 * 覆盖：正常入库 / 权限边界 / 重复上传覆盖不翻倍 / 公开列表 / 详情 / 非txt拒绝 / 分章器与段落切分器单测。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NovelUploadTest extends TestBase {

    @Autowired
    private NovelMapper novelMapper;
    @Autowired
    private NovelChapterMapper chapterMapper;
    @Autowired
    private NovelParagraphMapper paragraphMapper;

    private static final String PREFIX = "NovelUploadTest";

    /** 构造测试小说文本：两章、每章多个自然段，内容足够长（>200字/章，避免被最小间距过滤） */
    private static String buildNovelText() {
        StringBuilder sb = new StringBuilder();
        sb.append("测试小说（非正式内容，仅用于验证入库链路）\n\n");
        sb.append("第一章 测试之始\n");
        sb.append("\t这是第一章的第一段，讲述故事的开端。").append("这是一句没有实际含义的填充文本。".repeat(18)).append("\n");
        sb.append("\t这是第一章的第二段，角色登场。").append("这是另一句填充文本，用于凑足段落字数。".repeat(16)).append("\n");
        sb.append("\t这是第一章的第三段，一个简短段落。\n");
        sb.append("\n");
        sb.append("第二章 测试之续\n");
        sb.append("\t这是第二章的第一段，情节推进。").append("情节推进需要足够的内容支撑检索与统计。".repeat(17)).append("\n");
        sb.append("\t这是第二章的第二段，转折出现。").append("转折段落同样需要填充到足够长度。".repeat(15)).append("\n");
        sb.append("\t这是第二章的第三段。\n");
        sb.append("\t这是第二章的第四段。\n");
        return sb.toString();
    }

    private MvcResult upload(String token, String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/admin/novels/upload")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    @Test
    @Order(1)
    void uploadCreatesNovelAndData() throws Exception {
        String token = registerOrLogin(PREFIX + "_admin", "123456", "小说管理员");
        // 提升为 admin
        var admin = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.japy.entity.User>()
                .eq(com.japy.entity.User::getUsername, PREFIX + "_admin"));
        admin.setRole("admin");
        userMapper.updateById(admin);
        token = login(PREFIX + "_admin", "123456");

        String text = buildNovelText();
        MvcResult result = upload(token, "测试小说.txt", text);
        JsonNode node = body(result);
        assertEquals(200, node.get("code").asInt(), node.toString());

        JsonNode data = node.get("data");
        assertEquals("测试小说", data.get("title").asText());
        assertEquals(2, data.get("chapterCount").asInt());
        assertEquals(7, data.get("paragraphCount").asInt());   // 第一章3段 + 第二章4段
        assertTrue(data.get("totalChars").asInt() > 1000);
        assertTrue(data.get("maxParaChars").asInt() > 100);
        assertEquals("UTF-8", data.get("sourceEncoding").asText());
        assertEquals(2, data.get("chapters").size());

        // 数据库：novel / novel_chapter / novel_paragraph
        Novel novel = novelMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Novel>()
                .eq(Novel::getTitle, "测试小说"));
        assertNotNull(novel);
        assertEquals(1, novel.getStatus().intValue());
        assertEquals(2, novel.getChapterCount().intValue());
        assertEquals(7, novel.getParagraphCount().intValue());

        List<NovelChapter> chapters = chapterMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NovelChapter>()
                        .eq(NovelChapter::getNovelId, novel.getId()));
        assertEquals(2, chapters.size());
        assertEquals("第一章 测试之始", chapters.get(0).getTitle());
        assertEquals(3, chapters.get(0).getParagraphCount().intValue());
        assertTrue(chapters.get(0).getMaxParaChars() > 0);

        List<NovelParagraph> paras = paragraphMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NovelParagraph>()
                        .eq(NovelParagraph::getNovelId, novel.getId()));
        assertEquals(7, paras.size());
        // para_seq 从 0 连续编号
        assertEquals(0, paras.get(0).getParaSeq().intValue());
        assertEquals(2, paras.get(paras.size() - 1).getChapterNo().intValue());

        // 落盘目录结构：source/ chapters/ stats/
        Path dir = Path.of("novels", "测试小说");
        assertTrue(Files.exists(dir.resolve("source").resolve("测试小说.txt")));
        assertTrue(Files.exists(dir.resolve("chapters").resolve("001_第一章 测试之始.json")));
        assertTrue(Files.exists(dir.resolve("chapters").resolve("002_第二章 测试之续.json")));
        assertTrue(Files.exists(dir.resolve("stats").resolve("stats.json")));
        String stats = Files.readString(dir.resolve("stats").resolve("stats.json"));
        assertTrue(stats.contains("\"chapter_count\": 2"));
        assertTrue(stats.contains("\"paragraph_count\": 7"));
    }

    @Test
    @Order(2)
    void reuploadOverwritesWithoutDuplication() throws Exception {
        String token = login(PREFIX + "_admin", "123456");
        MvcResult result = upload(token, "测试小说.txt", buildNovelText());
        JsonNode node = body(result);
        assertEquals(200, node.get("code").asInt(), node.toString());
        // 同一本小说重新上传 → id 不变（覆盖）
        Novel first = novelMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Novel>()
                .eq(Novel::getTitle, "测试小说"));
        assertEquals(node.get("data").get("id").asLong(), first.getId().longValue());
        // 数据不翻倍
        assertEquals(2, chapterMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NovelChapter>()
                        .eq(NovelChapter::getNovelId, first.getId())).intValue());
        assertEquals(7, paragraphMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NovelParagraph>()
                        .eq(NovelParagraph::getNovelId, first.getId())).intValue());
    }

    @Test
    @Order(3)
    void rejectsNonTxtAndEmptyFile() throws Exception {
        String token = login(PREFIX + "_admin", "123456");
        MockMultipartFile bad = new MockMultipartFile(
                "file", "novel.pdf", "application/pdf", "fake".getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/admin/novels/upload")
                        .file(bad)
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertEquals(400, body(result).get("code").asInt());
    }

    @Test
    @Order(4)
    void uploadRequiresAdmin() throws Exception {
        // 未登录 → 401
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.txt", "text/plain", "第一章 a\n内容内容".getBytes(StandardCharsets.UTF_8));
        MvcResult anon = mockMvc.perform(multipart("/api/admin/novels/upload").file(file)).andReturn();
        assertEquals(401, anon.getResponse().getStatus());

        // 普通用户 → 403
        String userToken = registerOrLogin(PREFIX + "_user", "123456", "普通用户");
        MvcResult forbidden = mockMvc.perform(multipart("/api/admin/novels/upload")
                        .file(file)
                        .header("Authorization", bearer(userToken)))
                .andReturn();
        assertEquals(403, forbidden.getResponse().getStatus());
    }

    @Test
    @Order(5)
    void publicListAndAdminDetail() throws Exception {
        // 公开列表（未登录可访问；开发库可能有其他真实小说，只断言包含测试小说）
        MvcResult list = getReq("/api/novels?page=1&size=50", null);
        JsonNode listNode = body(list);
        assertEquals(200, listNode.get("code").asInt());
        assertTrue(listNode.get("data").get("total").asInt() >= 1);
        boolean found = false;
        for (JsonNode item : listNode.get("data").get("list")) {
            if ("测试小说".equals(item.get("title").asText())) { found = true; break; }
        }
        assertTrue(found, "公开列表应包含测试小说");

        // 管理端详情
        Novel novel = novelMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Novel>()
                .eq(Novel::getTitle, "测试小说"));
        MvcResult detail = getReq("/api/admin/novels/" + novel.getId(), login(PREFIX + "_admin", "123456"));
        JsonNode detailNode = body(detail);
        assertEquals(200, detailNode.get("code").asInt());
        assertEquals(2, detailNode.get("data").get("chapters").size());
        assertEquals("第一章 测试之始", detailNode.get("data").get("chapters").get(0).get("title").asText());
    }

    // ===== 纯单元级：分章器与段落切分器 =====

    @Test
    @Order(6)
    void chapterDetectorHandlesStandardFormat() {
        String text = "第1章 开始\n" + "x".repeat(300) + "\n"
                + "第2章 继续\n" + "y".repeat(300) + "\n"
                + "尾声 结束\n" + "z".repeat(300);
        List<ChapterDetector.Chapter> chapters = ChapterDetector.split(text);
        assertEquals(3, chapters.size());
        assertEquals("第1章 开始", chapters.get(0).title);
        assertEquals("尾声 结束", chapters.get(2).title);
    }

    @Test
    @Order(7)
    void chapterDetectorFallsBackOnNoTitles() {
        // 无章节标题 → 降级固定字数分章（20000字/章，段落边界断开）
        String text = "a\n".repeat(11000);   // 22000 字符，跨 2 章
        List<ChapterDetector.Chapter> chapters = ChapterDetector.split(text);
        assertEquals(2, chapters.size());
        assertTrue(chapters.get(0).title.startsWith("第"));
    }

    @Test
    @Order(8)
    void paragraphSplitterRespectsTabsAndBlankLines() {
        String text = "前言第一段\n前言第二段接续\n\n\t段A内容\n\t段B内容\n\n\t段C内容";
        List<String> paras = ParagraphSplitter.split(text);
        assertEquals(4, paras.size());
        assertEquals("前言第一段前言第二段接续", paras.get(0));  // 无缩进行拼接
        assertTrue(paras.get(1).startsWith("段A"));
        assertTrue(paras.get(3).startsWith("段C"));
    }

    @Test
    @Order(9)
    void deleteNovelRemovesDataAndDirectory() throws Exception {
        String token = login(PREFIX + "_admin", "123456");
        // 上传一本待删除的小说
        MvcResult up = upload(token, "待删除小说.txt", buildNovelText());
        assertEquals(200, body(up).get("code").asInt(), body(up).toString());

        Novel novel = novelMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Novel>()
                .eq(Novel::getTitle, "待删除小说"));
        assertNotNull(novel);

        // 详情返回源文件元信息
        JsonNode detail = body(getReq("/api/admin/novels/" + novel.getId(), token));
        assertEquals(200, detail.get("code").asInt());
        assertEquals("待删除小说.txt", detail.get("data").get("sourceName").asText());
        assertEquals("UTF-8", detail.get("data").get("sourceEncoding").asText());
        assertTrue(detail.get("data").get("sourceSize").asLong() > 0);
        assertTrue(detail.get("data").get("dirPath").asText().contains("待删除小说"));
        Path dir = Path.of("novels", "待删除小说");
        assertTrue(Files.exists(dir));

        // 删除：数据库 + 落盘目录
        assertEquals(200, body(deleteReq("/api/admin/novels/" + novel.getId(), token)).get("code").asInt());
        assertNull(novelMapper.selectById(novel.getId()));
        assertEquals(0, chapterMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NovelChapter>()
                        .eq(NovelChapter::getNovelId, novel.getId())).intValue());
        assertEquals(0, paragraphMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NovelParagraph>()
                        .eq(NovelParagraph::getNovelId, novel.getId())).intValue());
        assertFalse(Files.exists(dir), "落盘目录应被删除");

        // 重复删除 → 400
        assertEquals(400, body(deleteReq("/api/admin/novels/" + novel.getId(), token)).get("code").asInt());
    }

    @Test
    @Order(10)
    void publishMomentWithNovelLink() throws Exception {
        String token = login(PREFIX + "_admin", "123456");
        Novel novel = novelMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Novel>()
                .eq(Novel::getTitle, "测试小说"));
        assertNotNull(novel);

        // 发布动态并关联小说
        MvcResult r = postJson("/api/moments", token,
                "{\"content\":\"关联小说测试\",\"novelId\":" + novel.getId() + "}");
        assertEquals(200, body(r).get("code").asInt(), body(r).toString());
        JsonNode created = body(r).get("data");
        assertEquals(novel.getId().longValue(), created.get("novelId").asLong());
        long momentId = created.get("id").asLong();

        // 时间线返回 novelTitle
        MvcResult tl = getReq("/api/moments?page=1&size=5", token);
        JsonNode list = body(tl).get("data").get("list");
        boolean found = false;
        for (JsonNode m : list) {
            if ("关联小说测试".equals(m.get("content").asText())) {
                assertEquals("测试小说", m.get("novelTitle").asText());
                found = true;
            }
        }
        assertTrue(found, "时间线应返回关联小说名");

        // 关联不存在的小说 → 400
        assertEquals(400, body(postJson("/api/moments", token,
                "{\"content\":\"x\",\"novelId\":999999}")).get("code").asInt());

        // 清理测试动态
        assertEquals(200, body(deleteReq("/api/moments/" + momentId, token)).get("code").asInt());
    }

    @Test
    @Order(11)
    void cleanupDatabase() {
        // 清理测试小说数据（目录由 @AfterAll 清理），避免污染开发库
        Novel novel = novelMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Novel>()
                .eq(Novel::getTitle, "测试小说"));
        if (novel != null) {
            paragraphMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NovelParagraph>()
                    .eq(NovelParagraph::getNovelId, novel.getId()));
            chapterMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NovelChapter>()
                    .eq(NovelChapter::getNovelId, novel.getId()));
            novelMapper.deleteById(novel.getId());
        }
        assertEquals(0, novelMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Novel>()
                        .eq(Novel::getTitle, "测试小说")).intValue());
    }

    @AfterAll
    static void cleanup() {
        // 清理测试落盘目录（测试数据不入库残留）
        Path dir = Path.of("novels", "测试小说");
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }
    }
}
