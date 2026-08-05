package com.japy.novel;

import com.japy.module.novel.service.NovelParserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 小说解析服务单测：编码检测 / 章节切分 / 分段统计。
 */
class NovelParserServiceTest {

    private final NovelParserService parser = new NovelParserService();

    @Test
    void 标准章节标题切分() throws Exception {
        String txt = "第一章 序\n这是第一段。\n\n这是第二段。\n\n第二章 起程\n新的一章内容。\n\n第三章 终章\n最后内容。";
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain",
                txt.getBytes(StandardCharsets.UTF_8));
        var r = parser.parse(file);
        assertEquals(3, r.getChapters().size(), "应切出 3 章");
        assertEquals("第一章 序", r.getChapters().get(0).getTitle());
        assertEquals(2, r.getChapters().get(0).getParagraphs().size(), "第一章 2 段");
        assertEquals(1, r.getChapters().get(1).getParagraphs().size(), "第二章 1 段");
        assertTrue(r.getTotalChars() > 0, "总字数应为正");
    }

    @Test
    void 无章节标题整篇一章() throws Exception {
        String txt = "第一段内容。\n\n第二段内容。\n\n第三段内容。";
        MockMultipartFile file = new MockMultipartFile("file", "b.txt", "text/plain",
                txt.getBytes(StandardCharsets.UTF_8));
        var r = parser.parse(file);
        assertEquals(1, r.getChapters().size(), "无标题应整篇一章");
        assertEquals(3, r.getChapters().get(0).getParagraphs().size());
    }

    @Test
    void 章内统计正确() throws Exception {
        String txt = "第一章\n短段。\n\n这是一个比较长的段落用于验证最大段字数统计逻辑是否正确生效。";
        MockMultipartFile file = new MockMultipartFile("file", "c.txt", "text/plain",
                txt.getBytes(StandardCharsets.UTF_8));
        var r = parser.parse(file);
        var ch = r.getChapters().get(0);
        assertEquals(2, ch.getParagraphs().size());
        int longLen = "这是一个比较长的段落用于验证最大段字数统计逻辑是否正确生效。".length();
        assertEquals(longLen, ch.getMaxParaChars(), "最大段字数应正确");
        assertEquals("短段。".length() + longLen, ch.getChars(), "章字数应正确");
    }

    @Test
    void 编码检测GBK() throws Exception {
        String txt = "第一章 测试\nGBK 编码内容。";
        MockMultipartFile file = new MockMultipartFile("file", "d.txt", "text/plain",
                txt.getBytes(java.nio.charset.Charset.forName("GBK")));
        var r = parser.parse(file);
        assertEquals("第一章 测试", r.getChapters().get(0).getTitle(), "GBK 应被正确解码");
    }

    @Test
    void 统计字数排除章节标题行() throws Exception {
        // 章节标题行不算入正文字数（标题仅作为元数据）
        String txt = "第一章 标题\n正文内容一。\n\n正文内容二。";
        MockMultipartFile file = new MockMultipartFile("file", "e.txt", "text/plain",
                txt.getBytes(StandardCharsets.UTF_8));
        var r = parser.parse(file);
        var ch = r.getChapters().get(0);
        int expect = "正文内容一。".length() + "正文内容二。".length();
        assertEquals(expect, ch.getChars(), "标题不应计入正文字数");
    }
}
