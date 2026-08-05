package com.japy.module.novel.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小说文件解析服务：
 * 1. 编码检测（UTF-8 BOM / GBK 回退）
 * 2. 按章节标题切章（第X章/回/节/卷 + Chapter/第X部分 等常见格式）
 * 3. 章内按空行分段
 * 4. 统计每章字数/段数/最大段字数
 * 5. 落盘目录结构：data/novels/{novelId}_{title}/{source.txt, meta.json, chapters/NN_标题.txt}
 * 流式逐行读取，百万字级文件内存可控。
 */
@Slf4j
@Service
public class NovelParserService {

    /** 章节标题正则：第X章/回/节/卷/部分，或 Chapter N / 序章 / 楔子 等 */
    private static final Pattern CHAPTER_TITLE = Pattern.compile(
            "^(\\s{0,4}(第[0-9零一二三四五六七八九十百千万两]+[章回节卷部篇集]"
                    + "|序章|序言|楔子|引子|尾声|后记|番外|Chapter\\s*[0-9]+|chapter\\s*[0-9]+)"
                    + "\\s*[：:、\\s]?.*)$");

    @Value("${novel.storage-dir:data/novels}")
    private String storageDir;

    /** 解析结果：章节列表 + 总统计 */
    @Data
    public static class ParseResult {
        private List<Chapter> chapters = new ArrayList<>();
        private long totalChars;
    }

    @Data
    public static class Chapter {
        private String title = "";
        private List<String> paragraphs = new ArrayList<>();
        private int chars;
        private int maxParaChars;
    }

    /** 解析上传的 txt 文件（自动识别编码） */
    public ParseResult parse(MultipartFile file) throws IOException {
        String encoding = detectEncoding(file);
        log.info("解析文件: {} 编码: {}", file.getOriginalFilename(), encoding);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), Charset.forName(encoding)))) {
            return doParse(reader);
        }
    }

    /** 解析磁盘文件 */
    public ParseResult parse(Path path) throws IOException {
        String encoding = detectEncoding(path);
        try (BufferedReader reader = Files.newBufferedReader(path, Charset.forName(encoding))) {
            return doParse(reader);
        }
    }

    /** 核心解析：逐行切章、分段、统计（标题只认正则匹配行，普通行一律为正文） */
    private ParseResult doParse(BufferedReader reader) throws IOException {
        ParseResult result = new ParseResult();
        Chapter current = new Chapter();
        List<String> pendingParas = new ArrayList<>();
        StringBuilder para = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            // 空行 → 结束当前段落
            if (trimmed.isEmpty()) {
                flushPara(para, pendingParas);
                continue;
            }
            // 章节标题（仅正则匹配行）
            Matcher m = CHAPTER_TITLE.matcher(trimmed);
            if (m.matches()) {
                // 当前章已有内容 → 收尾开新章
                if (!current.getTitle().isEmpty() && !pendingParas.isEmpty()) {
                    finishChapter(current, pendingParas, result);
                    current = new Chapter();
                    pendingParas = new ArrayList<>();
                }
                if (current.getTitle().isEmpty()) {
                    current.setTitle(trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed);
                }
            } else {
                para.append(trimmed);
            }
        }
        flushPara(para, pendingParas);
        if (!current.getTitle().isEmpty() || !pendingParas.isEmpty()) {
            finishChapter(current, pendingParas, result);
        }
        result.setTotalChars(result.getChapters().stream().mapToLong(Chapter::getChars).sum());
        return result;
    }

    private void flushPara(StringBuilder para, List<String> pendingParas) {
        if (para.length() > 0) {
            pendingParas.add(para.toString().trim());
            para.setLength(0);
        }
    }

    private void finishChapter(Chapter ch, List<String> paras, ParseResult result) {
        if (ch.getTitle().isEmpty()) {
            ch.setTitle("第" + (result.getChapters().size() + 1) + "章");
        }
        ch.setParagraphs(new ArrayList<>(paras));
        int chars = ch.getParagraphs().stream().mapToInt(String::length).sum();
        int maxPara = ch.getParagraphs().stream().mapToInt(String::length).max().orElse(0);
        ch.setChars(chars);
        ch.setMaxParaChars(maxPara);
        result.getChapters().add(ch);
    }

    /** 编码检测：BOM → UTF-8；否则尝试 UTF-8 严格解码，失败回退 GBK */
    public String detectEncoding(MultipartFile file) throws IOException {
        return detectEncoding(file.getInputStream());
    }

    public String detectEncoding(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return detectEncoding(in);
        }
    }

    private String detectEncoding(InputStream in) throws IOException {
        byte[] head = in.readNBytes(3);
        if (head.length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
            return "UTF-8";   // BOM
        }
        if (head.length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE) {
            return "UTF-16LE";
        }
        // 无 BOM：抽样前 64KB 尝试 UTF-8 严格解码
        byte[] sample;
        try (InputStream full = in) {
            sample = full.readNBytes(64 * 1024);
        }
        try {
            CharsetDecoder strict = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            strict.decode(java.nio.ByteBuffer.wrap(sample));
            return "UTF-8";
        } catch (Exception e) {
            return "GBK";
        }
    }

    /** 落盘：创建小说目录结构并保存源文件 + 章节文件 + meta */
    public Path saveFiles(Long novelId, String title, MultipartFile source,
                          ParseResult result) throws IOException {
        String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        Path dir = Paths.get(storageDir, novelId + "_" + safeTitle);
        Files.createDirectories(dir.resolve("chapters"));

        // 1. 源文件
        String ext = "txt";
        String orig = source.getOriginalFilename();
        if (orig != null && orig.contains(".")) {
            ext = orig.substring(orig.lastIndexOf('.') + 1);
        }
        source.transferTo(dir.resolve("source." + ext).toFile());

        // 2. 章节文件（章节号_标题.txt）+ meta.json
        StringBuilder meta = new StringBuilder("{\"novelId\":").append(novelId)
                .append(",\"totalChars\":").append(result.getTotalChars())
                .append(",\"chapters\":[");
        for (int i = 0; i < result.getChapters().size(); i++) {
            NovelParserService.Chapter ch = result.getChapters().get(i);
            String fn = String.format("%03d_%s.txt", i + 1, ch.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_"));
            Path cp = dir.resolve("chapters").resolve(fn);
            Files.writeString(cp, String.join("\n\n", ch.getParagraphs()), StandardCharsets.UTF_8);
            if (i > 0) meta.append(",");
            meta.append("{\"no\":").append(i + 1)
                    .append(",\"title\":\"").append(escapeJson(ch.getTitle()))
                    .append("\",\"chars\":").append(ch.getChars())
                    .append(",\"paragraphs\":").append(ch.getParagraphs().size())
                    .append(",\"maxParaChars\":").append(ch.getMaxParaChars())
                    .append("}");
        }
        meta.append("]}");
        Files.writeString(dir.resolve("meta.json"), meta.toString(), StandardCharsets.UTF_8);
        return dir;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
