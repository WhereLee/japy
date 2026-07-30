package com.recloud.service.imports;

import com.recloud.entity.Chapter;
import com.recloud.entity.Novel;
import com.recloud.mapper.ChapterMapper;
import com.recloud.mapper.NovelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * TXT 格式小说导入服务
 * <p>
 * 继承 NovelImportService 模板方法，实现 TXT 格式的导入逻辑。
 * 章节识别规则：第X章、第X回、第X节、第X幕（支持中文/阿拉伯数字）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TxtNovelImportService extends NovelImportService {

    private final NovelMapper novelMapper;
    private final ChapterMapper chapterMapper;

    /** 章节标题正则 */
    private static final Pattern CHAPTER_TITLE_PATTERN = Pattern.compile(
            "^\\s*第[零一二三四五六七八九十百千万\\d]+[章回节幕].*$"
    );

    @Override
    protected Charset detectCharset(Path filePath) throws IOException {
        byte[] buf = new byte[8192];
        int read;
        try (var is = Files.newInputStream(filePath)) {
            read = is.read(buf);
        }
        if (read <= 0) return StandardCharsets.UTF_8;
        String sample = new String(buf, 0, read, StandardCharsets.UTF_8);
        if (!sample.contains("\uFFFD")) {
            return StandardCharsets.UTF_8;
        }
        log.info("文件非 UTF-8 编码，使用 GBK 读取: {}", filePath.getFileName());
        return Charset.forName("GBK");
    }

    @Override
    protected List<ChapterInfo> scanChapters(Path filePath, Charset charset) throws IOException {
        List<Integer> chapterLineIndexes = new ArrayList<>();
        List<String> chapterTitles = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            String line;
            int lineIndex = 0;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (CHAPTER_TITLE_PATTERN.matcher(trimmed).matches()) {
                    chapterLineIndexes.add(lineIndex);
                    chapterTitles.add(trimmed);
                    log.debug("匹配到章节 L{}: {}", lineIndex, trimmed);
                }
                lineIndex++;
            }
        }

        List<ChapterInfo> result = new ArrayList<>();

        if (chapterLineIndexes.isEmpty()) {
            result.add(new ChapterInfo("正文", 0, Integer.MAX_VALUE));
            return result;
        }

        // 前言
        if (chapterLineIndexes.get(0) > 0) {
            result.add(new ChapterInfo("前言", 0, chapterLineIndexes.get(0) - 1));
        }

        // 各章节
        for (int i = 0; i < chapterLineIndexes.size(); i++) {
            int start = chapterLineIndexes.get(i);
            int end = (i + 1 < chapterLineIndexes.size())
                    ? chapterLineIndexes.get(i + 1) - 1
                    : Integer.MAX_VALUE;
            result.add(new ChapterInfo(chapterTitles.get(i), start, end));
        }

        return result;
    }

    @Override
    protected List<Chapter> extractContent(Path filePath, Charset charset, List<ChapterInfo> chapterInfos) throws IOException {
        List<Chapter> chapters = new ArrayList<>();

        for (int i = 0; i < chapterInfos.size(); i++) {
            ChapterInfo info = chapterInfos.get(i);
            String content = readLines(filePath, charset, info.startLine, info.endLine);

            Chapter chapter = new Chapter();
            chapter.setTitle(info.title);
            chapter.setContent(content);
            chapter.setChapterOrder(i + 1);
            chapters.add(chapter);
        }

        return chapters;
    }

    @Override
    protected void persist(Novel novel, List<Chapter> chapters) {
        novelMapper.insert(novel);
        for (Chapter chapter : chapters) {
            chapter.setNovelId(novel.getId());
            chapterMapper.insert(chapter);
        }
    }

    @Override
    protected String getFormatName() {
        return "TXT";
    }

    /**
     * 按行范围读取章节内容
     */
    private String readLines(Path filePath, Charset charset, int startLine, int endLine) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            String line;
            int lineIndex = 0;
            while ((line = reader.readLine()) != null) {
                if (lineIndex >= startLine && (endLine == Integer.MAX_VALUE || lineIndex <= endLine)) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
                if (lineIndex > endLine) break;
                lineIndex++;
            }
        }
        return sb.toString().trim();
    }
}
