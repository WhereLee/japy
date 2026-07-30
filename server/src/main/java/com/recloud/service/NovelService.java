package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import com.recloud.entity.Annotation;
import com.recloud.entity.AnnotationLike;
import com.recloud.entity.Chapter;
import com.recloud.entity.Comment;
import com.recloud.entity.Novel;
import com.recloud.mapper.AnnotationLikeMapper;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.mapper.ChapterMapper;
import com.recloud.mapper.CommentMapper;
import com.recloud.mapper.NovelMapper;
import com.recloud.service.imports.TxtNovelImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NovelService implements CommandLineRunner {

    private final NovelMapper novelMapper;
    private final ChapterMapper chapterMapper;
    private final AnnotationMapper annotationMapper;
    private final CommentMapper commentMapper;
    private final AnnotationLikeMapper likeMapper;
    private final TxtNovelImportService txtNovelImportService;

    @Value("${novel.txt-dir:./novels}")
    private String txtDir;

    // ==================== 启动时自动扫描导入 ====================

    @Override
    public void run(String... args) {
        scanAndImport();
    }

    /**
     * 扫描 novels/ 目录，导入未入库的 txt 文件
     * 委托 TxtNovelImportService 处理实际导入逻辑
     */
    public void scanAndImport() {
        Path dir = Paths.get(txtDir);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                log.info("创建小说目录: {}", dir.toAbsolutePath());
            } catch (IOException e) {
                log.error("无法创建小说目录: {}", dir.toAbsolutePath(), e);
                return;
            }
        }

        try {
            Files.list(dir)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .forEach(this::importIfNew);
        } catch (IOException e) {
            log.error("扫描小说目录失败: {}", dir.toAbsolutePath(), e);
        }
    }

    /**
     * 导入单个 txt 文件（按文件名去重），委托给 TxtNovelImportService
     */
    private void importIfNew(Path txtPath) {
        String fileName = txtPath.getFileName().toString();

        Long count = novelMapper.selectCount(
                new LambdaQueryWrapper<Novel>().eq(Novel::getFileName, fileName)
        );
        if (count > 0) {
            log.debug("小说已导入，跳过: {}", fileName);
            return;
        }

        try {
            String title = fileName.replace(".txt", "");
            txtNovelImportService.importNovel(txtPath, title);
            log.info("导入小说成功: {}", title);
        } catch (IOException e) {
            log.error("读取小说文件失败: {}", fileName, e);
        }
    }

    // ==================== 查询 API ====================

    public List<Novel> listNovels() {
        return novelMapper.selectList(
                new LambdaQueryWrapper<Novel>().orderByDesc(Novel::getCreatedAt)
        );
    }

    /**
     * 管理员分页查询小说
     */
    public IPage<Novel> listNovels(int page, int size) {
        Page<Novel> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Novel> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Novel::getCreatedAt);
        return novelMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 管理员删除小说（级联删除章节→批注→评论/点赞）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean adminDeleteNovel(Long id) {
        Novel novel = novelMapper.selectById(id);
        if (novel == null) return false;

        // 1. 查出该小说所有章节
        List<Chapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>().eq(Chapter::getNovelId, id));
        List<Long> chapterIds = chapters.stream().map(Chapter::getId).toList();

        if (!chapterIds.isEmpty()) {
            // 2. 查出这些章节下的所有批注
            List<Annotation> annotations = annotationMapper.selectList(
                    new LambdaQueryWrapper<Annotation>().in(Annotation::getChapterId, chapterIds));
            List<Long> annotationIds = annotations.stream().map(Annotation::getId).toList();

            if (!annotationIds.isEmpty()) {
                // 3. 级联删除评论和点赞
                commentMapper.delete(
                        new LambdaQueryWrapper<Comment>().in(Comment::getAnnotationId, annotationIds));
                likeMapper.delete(
                        new LambdaQueryWrapper<AnnotationLike>().in(AnnotationLike::getAnnotationId, annotationIds));
                // 4. 删除批注
                annotationMapper.deleteBatchIds(annotationIds);
            }
            // 5. 删除章节
            chapterMapper.deleteBatchIds(chapterIds);
        }

        // 6. 删除小说
        novelMapper.deleteById(id);
        log.info("管理员删除小说完成: novelId={}, title={}, 级联删除{}章", id, novel.getTitle(), chapterIds.size());
        return true;
    }

    public Novel getNovel(Long id) {
        Novel novel = novelMapper.selectById(id);
        if (novel == null) {
            throw new BizException(ResultCode.NOVEL_NOT_FOUND);
        }
        return novel;
    }

    public List<Chapter> listChapters(Long novelId) {
        return chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getNovelId, novelId)
                        .orderByAsc(Chapter::getChapterOrder)
                        .select(Chapter::getId, Chapter::getTitle, Chapter::getChapterOrder, Chapter::getNovelId)
        );
    }

    /**
     * 章节内容查询（Caffeine L1 缓存）
     * <p>
     * 防缓存击穿：sync=true 保证同一 chapterId 的缓存 miss 只有一个线程去 DB 加载，
     * 其余线程等待。配合 refreshAfterWrite 异步刷新，绝大多数请求直接命中缓存。
     */
    @Cacheable(cacheNames = "chapterContent", key = "#chapterId", sync = true)
    public Chapter getChapter(Long chapterId) {
        log.debug("章节内容缓存未命中，查询DB: chapterId={}", chapterId);
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BizException(ResultCode.CHAPTER_NOT_FOUND);
        }
        return chapter;
    }
}
