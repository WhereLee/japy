package com.recloud.service.imports;

import com.recloud.entity.Chapter;
import com.recloud.entity.Novel;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

/**
 * 小说导入服务 — 模板方法模式
 * <p>
 * 定义导入流程的骨架：detectCharset() → scanChapters() → extractContent() → persist()
 * 每个步骤是抽象/可覆盖方法，子类实现具体格式的逻辑。
 * <p>
 * 当前实现：TxtNovelImportService（TXT 格式）
 * 未来可扩展：PdfNovelImportService、EpubNovelImportService
 * <p>
 * 模板方法的优势：
 * - 导入流程统一，便于维护和扩展
 * - 新增格式只需继承并实现抽象方法
 * - 符合开闭原则（OCP）和依赖倒置原则（DIP）
 */
@Slf4j
public abstract class NovelImportService {

    /**
     * 模板方法：定义导入流程骨架
     * <p>
     * 1. detectCharset — 检测文件编码
     * 2. scanChapters — 扫描章节结构
     * 3. extractContent — 提取章节内容
     * 4. persist — 持久化到数据库
     */
    public final void importNovel(Path filePath, String title) throws IOException {
        log.info("开始导入小说: {} (格式: {})", title, getFormatName());

        // Step 1: 检测编码
        Charset charset = detectCharset(filePath);
        log.debug("检测到编码: {}", charset.name());

        // Step 2: 扫描章节结构
        List<ChapterInfo> chapterInfos = scanChapters(filePath, charset);
        log.info("扫描到 {} 个章节", chapterInfos.size());

        // Step 3: 提取章节内容
        List<Chapter> chapters = extractContent(filePath, charset, chapterInfos);

        // Step 4: 持久化
        Novel novel = buildNovel(title, filePath.getFileName().toString());
        persist(novel, chapters);

        log.info("导入小说完成: {} ({}章)", title, chapters.size());
    }

    /** 检测文件编码 */
    protected abstract Charset detectCharset(Path filePath) throws IOException;

    /** 扫描章节结构（返回章节信息列表，不含内容） */
    protected abstract List<ChapterInfo> scanChapters(Path filePath, Charset charset) throws IOException;

    /** 提取章节内容 */
    protected abstract List<Chapter> extractContent(Path filePath, Charset charset, List<ChapterInfo> chapterInfos) throws IOException;

    /** 构建 Novel 实体（子类可覆盖以设置格式特有字段） */
    protected Novel buildNovel(String title, String fileName) {
        Novel novel = new Novel();
        novel.setTitle(title);
        novel.setAuthor("");
        novel.setDescription("");
        novel.setFileName(fileName);
        return novel;
    }

    /** 持久化到数据库 */
    protected abstract void persist(Novel novel, List<Chapter> chapters);

    /** 返回格式名称 */
    protected abstract String getFormatName();

    /**
     * 章节信息（标题 + 行范围），扫描阶段使用
     */
    public static class ChapterInfo {
        public final String title;
        public final int startLine;
        public final int endLine;

        public ChapterInfo(String title, int startLine, int endLine) {
            this.title = title;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }
}
