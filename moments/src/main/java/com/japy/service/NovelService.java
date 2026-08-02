package com.japy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.entity.Novel;
import com.japy.entity.NovelChapter;
import com.japy.entity.NovelParagraph;
import com.japy.mapper.NovelChapterMapper;
import com.japy.mapper.NovelMapper;
import com.japy.mapper.NovelParagraphMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小说入库管线：上传 txt → 编码检测 → 章节检测 → 自然段切分 → 统计
 * → 落盘目录（{novels-dir}/{小说名}/{source|chapters|stats}）→ 写入数据库。
 *
 * 设计：
 * - 目录是"审计产物"（人可读、可追溯、与 pyser 产出可交叉验证），数据库是"查询数据"
 * - 同名小说重复上传 = 覆盖重导（幂等，管理员重新上传是常见操作）
 * - 同步处理：单文件毫秒~秒级，不需要异步任务队列
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelService {

    private final NovelMapper novelMapper;
    private final NovelChapterMapper chapterMapper;
    private final NovelParagraphMapper paragraphMapper;

    @Value("${app.novels-dir:novels}")
    private String novelsDir;

    /** 章节目录文件名：{no:03d}_{标题前20字}.json */
    private static final int FILE_TITLE_LEN = 20;
    /** 前言阈值：第一个标题前的内容超过 200 字才作为"前言"章节 */
    private static final int PREFACE_MIN_CHARS = 200;
    /** Windows 非法文件名字符 */
    private static final String ILLEGAL_FS = "\\/:*?\"<>|";

    // ============ 上传入口 ============

    public Map<String, Object> upload(MultipartFile file, String author) {
        long start = System.currentTimeMillis();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的 txt 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".txt")) {
            throw new IllegalArgumentException("仅支持 txt 文件");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("文件读取失败: " + e.getMessage());
        }

        // 1. 编码检测（UTF-8 BOM > UTF-8 > GBK，与 pyser 一致）
        String encoding = detectEncoding(bytes);
        String text = new String(stripBom(bytes), Charset.forName("gbk".equalsIgnoreCase(encoding) ? "GBK" : "UTF-8"));

        // 2. 章节检测（7 组正则 + 验证 + 降级）
        List<ChapterDetector.Chapter> chapters = ChapterDetector.split(text);
        if (chapters.isEmpty()) {
            throw new IllegalArgumentException("未识别出任何章节，请检查 txt 内容");
        }

        // 3. 自然段切分 + 每章统计
        List<ChapterData> chapterData = new ArrayList<>();
        int totalChars = 0, totalParas = 0, maxParaAll = 0;
        for (ChapterDetector.Chapter ch : chapters) {
            List<String> paras = ParagraphSplitter.split(ch.content);
            int maxPara = paras.stream().mapToInt(String::length).max().orElse(0);
            int paraCount = paras.size();
            chapterData.add(new ChapterData(ch.index + 1, ch.title, ch.content, ch.chars, paraCount, maxPara, paras));
            totalChars += ch.chars;
            totalParas += paraCount;
            maxParaAll = Math.max(maxParaAll, maxPara);
        }

        // 4. 落盘目录（覆盖旧产出）
        Path dir = writeDirectory(filename, encoding, bytes, text, chapterData);

        // 5. 入库（同名覆盖）
        Novel novel = new Novel();
        novel.setTitle(titleFromFile(filename));
        novel.setAuthor(author == null || author.isBlank() ? null : author.strip());
        novel.setStatus(1);
        novel.setChapterCount(chapterData.size());
        novel.setParagraphCount(totalParas);
        novel.setTotalChars(totalChars);
        novel.setSourceName(filename);
        novel.setSourceSize((long) bytes.length);
        novel.setSourceEncoding(encoding);
        novel.setDirPath(dir.toString().replace('\\', '/'));
        persist(novel, chapterData);

        // 6. 返回结果（对齐前端展示结构）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", novel.getId());
        data.put("title", novel.getTitle());
        data.put("author", novel.getAuthor());
        data.put("status", 1);
        data.put("chapterCount", chapterData.size());
        data.put("paragraphCount", totalParas);
        data.put("totalChars", totalChars);
        data.put("maxParaChars", maxParaAll);
        data.put("sourceName", filename);
        data.put("sourceSize", bytes.length);
        data.put("sourceEncoding", encoding);
        data.put("directory", dir.toString().replace('\\', '/'));
        data.put("costMs", (int) (System.currentTimeMillis() - start));
        List<Map<String, Object>> chapterList = new ArrayList<>();
        for (ChapterData cd : chapterData) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("no", cd.no());
            m.put("title", cd.title());
            m.put("chars", cd.chars());
            m.put("paragraphCount", cd.paragraphCount());
            m.put("maxParaChars", cd.maxParaChars());
            chapterList.add(m);
        }
        data.put("chapters", chapterList);
        return data;
    }

    // ============ 入库 ============

    /** 入库（事务）：同名小说覆盖重导，数据不翻倍。章节/段落批量插入（避免逐条 INSERT 的网络往返） */
    @Transactional
    protected void persist(Novel novel, List<ChapterData> chapterData) {
        Novel existing = novelMapper.selectOne(new LambdaQueryWrapper<Novel>().eq(Novel::getTitle, novel.getTitle()));
        if (existing != null) {
            chapterMapper.delete(new LambdaQueryWrapper<NovelChapter>().eq(NovelChapter::getNovelId, existing.getId()));
            paragraphMapper.delete(new LambdaQueryWrapper<NovelParagraph>().eq(NovelParagraph::getNovelId, existing.getId()));
            novel.setId(existing.getId());
            novelMapper.updateById(novel);
        } else {
            novelMapper.insert(novel);
        }

        // 章节批量插入
        List<NovelChapter> chapters = new ArrayList<>();
        for (ChapterData cd : chapterData) {
            NovelChapter nc = new NovelChapter();
            nc.setNovelId(novel.getId());
            nc.setChapterNo(cd.no());
            nc.setTitle(cd.title());
            nc.setChars(cd.chars());
            nc.setParagraphCount(cd.paragraphCount());
            nc.setMaxParaChars(cd.maxParaChars());
            chapters.add(nc);
        }
        chapterMapper.batchInsert(chapters);

        // 段落批量插入（每批 500 条，一条 SQL 多条 VALUES）
        List<NovelParagraph> batch = new ArrayList<>();
        for (ChapterData cd : chapterData) {
            List<String> paras = cd.paragraphs();
            for (int seq = 0; seq < paras.size(); seq++) {
                NovelParagraph np = new NovelParagraph();
                np.setNovelId(novel.getId());
                np.setChapterNo(cd.no());
                np.setParaSeq(seq);
                np.setContent(paras.get(seq));
                np.setChars(paras.get(seq).length());
                batch.add(np);
                if (batch.size() >= BATCH_SIZE) {
                    paragraphMapper.batchInsert(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            paragraphMapper.batchInsert(batch);
        }
    }

    /** 段落批量插入批大小（500 条 × 5 参数 = 2500，远低于 PG 参数上限 65535） */
    private static final int BATCH_SIZE = 500;

    // ============ 删除 ============

    /**
     * 删除小说：数据库数据（段落/章节/小说）+ 落盘目录。
     * 返回 false 表示小说不存在。
     */
    @Transactional
    public boolean delete(Long id) {
        Novel novel = novelMapper.selectById(id);
        if (novel == null) return false;
        paragraphMapper.delete(new LambdaQueryWrapper<NovelParagraph>().eq(NovelParagraph::getNovelId, id));
        chapterMapper.delete(new LambdaQueryWrapper<NovelChapter>().eq(NovelChapter::getNovelId, id));
        novelMapper.deleteById(id);

        // 删除落盘目录（目录删除失败不影响数据库删除结果）
        if (novel.getDirPath() != null && !novel.getDirPath().isBlank()) {
            try {
                Path dir = Path.of(novel.getDirPath());
                if (Files.exists(dir)) {
                    try (var walk = Files.walk(dir)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
                    }
                }
            } catch (Exception e) {
                log.warn("删除小说目录失败: {}", e.getMessage());
            }
        }
        return true;
    }

    // ============ 落盘 ============

    private Path writeDirectory(String filename, String encoding, byte[] bytes, String text,
                                List<ChapterData> chapterData) {
        try {
            String title = titleFromFile(filename);
            Path root = Path.of(novelsDir).toAbsolutePath().normalize();
            Path dir = root.resolve(safeName(title));

            // 覆盖旧产出（重新入库避免残留文件）
            if (Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                }
            }
            Path sourceDir = Files.createDirectories(dir.resolve("source"));
            Path chaptersDir = Files.createDirectories(dir.resolve("chapters"));
            Path statsDir = Files.createDirectories(dir.resolve("stats"));

            // source/ 源文件（保留原始字节，不改编码；统一编码副本由 source.txt 提供）
            Files.write(sourceDir.resolve(safeName(filename)), bytes);

            // chapters/ 每章一个 json（{"title","content"}，与 pyser 产出结构一致）
            for (ChapterData cd : chapterData) {
                String safeTitle = safeName(cd.title());
                String fileTitle = safeTitle.length() > FILE_TITLE_LEN ? safeTitle.substring(0, FILE_TITLE_LEN) : safeTitle;
                String chapterFile = String.format("%03d_%s.json", cd.no(), fileTitle);
                String json = "{\"title\": " + jsonEscape(cd.title())
                        + ", \"content\": " + jsonEscape(cd.content()) + "}";
                Files.writeString(chaptersDir.resolve(chapterFile), json, StandardCharsets.UTF_8);
            }

            // stats/ 统计信息（备用/审计）
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"novel_title\": ").append(jsonEscape(title)).append(",\n");
            sb.append("  \"source_file\": ").append(jsonEscape(filename)).append(",\n");
            sb.append("  \"source_encoding\": ").append(jsonEscape(encoding)).append(",\n");
            sb.append("  \"chapter_count\": ").append(chapterData.size()).append(",\n");
            sb.append("  \"total_chars\": ").append(chapterData.stream().mapToInt(ChapterData::chars).sum()).append(",\n");
            sb.append("  \"paragraph_count\": ").append(chapterData.stream().mapToInt(ChapterData::paragraphCount).sum()).append(",\n");
            sb.append("  \"chapters\": [\n");
            for (int i = 0; i < chapterData.size(); i++) {
                ChapterData cd = chapterData.get(i);
                sb.append("    {\"no\": ").append(cd.no())
                        .append(", \"title\": ").append(jsonEscape(cd.title()))
                        .append(", \"chars\": ").append(cd.chars())
                        .append(", \"paragraph_count\": ").append(cd.paragraphCount())
                        .append(", \"max_para_chars\": ").append(cd.maxParaChars()).append("}");
                sb.append(i < chapterData.size() - 1 ? ",\n" : "\n");
            }
            sb.append("  ]\n}\n");
            Files.writeString(statsDir.resolve("stats.json"), sb.toString(), StandardCharsets.UTF_8);

            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("小说文件写入失败: " + e.getMessage(), e);
        }
    }

    // ============ 工具 ============

    /** 编码检测：UTF-8 BOM > UTF-8 > GBK（只检测前 64KB，与 pyser 读前 4KB 同理） */
    private static String detectEncoding(byte[] bytes) {
        int n = Math.min(bytes.length, 64 * 1024);
        if (n >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return "UTF-8";
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes, 0, n));
            return "UTF-8";
        } catch (CharacterCodingException e) {
            return "GBK";
        }
    }

    /** 剥离 UTF-8 BOM */
    private static byte[] stripBom(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            byte[] out = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, out, 0, out.length);
            return out;
        }
        return bytes;
    }

    /** 小说标题取自文件名（去 .txt 后缀） */
    private static String titleFromFile(String filename) {
        String name = filename;
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.strip();
    }

    /** 过滤文件系统非法字符 */
    private static String safeName(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (ILLEGAL_FS.indexOf(c) >= 0 || c < 0x20) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().strip();
    }

    /** 极简 JSON 字符串转义（用于落盘章节/统计文件） */
    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    /** 章节目录下的章节数据（含段落） */
    private record ChapterData(int no, String title, String content, int chars,
                               int paragraphCount, int maxParaChars, List<String> paragraphs) {}
}
