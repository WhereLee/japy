package com.japy.module.novel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.BusinessException;
import com.japy.common.PageResult;
import com.japy.module.novel.entity.Novel;
import com.japy.module.novel.entity.NovelChapter;
import com.japy.module.novel.entity.NovelParagraph;
import com.japy.module.novel.entity.NovelReadProgress;
import com.japy.module.novel.mapper.NovelChapterMapper;
import com.japy.module.novel.mapper.NovelMapper;
import com.japy.module.novel.mapper.NovelParagraphMapper;
import com.japy.module.novel.mapper.NovelReadProgressMapper;
import com.japy.module.novel.vo.ChapterVO;
import com.japy.module.novel.vo.NovelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 小说阅读服务：列表 / 详情 / 章节内容（含上下章）/ 阅读进度。
 * 设计要点（调研 novel 开源项目）：
 * - 章节内容按段落存储，读取时按序拼接 → 段落为 RAG 检索最小单元
 * - 上/下一章服务端计算（按 chapter_no 排序 LIMIT 1），前端零额外请求
 * - 进度存"章内字符偏移"，跨字号/跨设备稳定
 */
@Service
@RequiredArgsConstructor
public class NovelService {

    private final NovelMapper novelMapper;
    private final NovelChapterMapper chapterMapper;
    private final NovelParagraphMapper paragraphMapper;
    private final NovelReadProgressMapper progressMapper;

    /** 小说列表（分页 + 可选关键词/分类）——用户端：仅上架(0)，未删除 */
    public PageResult<NovelVO> listNovels(int page, int size, String keyword, String category) {
        Page<Novel> p = novelMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Novel>()
                        .eq(Novel::getStatus, 0)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(Novel::getTitle, keyword).or().like(Novel::getAuthor, keyword))
                        .eq(category != null && !category.isBlank(), Novel::getCategory, category)
                        .orderByDesc(Novel::getUpdateTime));
        List<NovelVO> list = p.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, p.getTotal(), page, size);
    }

    /** 管理端列表（含草稿/下架） */
    public PageResult<NovelVO> adminList(int page, int size, String keyword) {
        Page<Novel> p = novelMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Novel>()
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(Novel::getTitle, keyword).or().like(Novel::getAuthor, keyword))
                        .orderByDesc(Novel::getCreateTime));
        return PageResult.of(p.getRecords().stream().map(this::toVO).toList(), p.getTotal(), page, size);
    }

    /** 小说详情——校验可读状态（仅上架） */
    public NovelVO detail(Long novelId) {
        Novel novel = novelMapper.selectById(novelId);
        if (novel == null || novel.getStatus() == null || novel.getStatus() != 0) {
            throw new BusinessException("小说不存在或未上架");
        }
        return toVO(novel);
    }

    /** 管理端详情（任意状态） */
    public NovelVO adminDetail(Long novelId) {
        Novel novel = novelMapper.selectById(novelId);
        if (novel == null) {
            throw new BusinessException("小说不存在");
        }
        return toVO(novel);
    }

    /** 章节列表（懒加载分页；列表接口不查 content 表） */
    public PageResult<NovelChapter> listChapters(Long novelId, int page, int size) {
        Page<NovelChapter> p = chapterMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<NovelChapter>()
                        .eq(NovelChapter::getNovelId, novelId)
                        .orderByAsc(NovelChapter::getChapterNo));
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    /** 章节内容（段落拼接 + 上/下一章 id） */
    public ChapterVO chapterContent(Long chapterId) {
        NovelChapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        List<NovelParagraph> paras = paragraphMapper.selectList(
                new LambdaQueryWrapper<NovelParagraph>()
                        .eq(NovelParagraph::getNovelId, chapter.getNovelId())
                        .eq(NovelParagraph::getChapterNo, chapter.getChapterNo())
                        .orderByAsc(NovelParagraph::getParaSeq));
        List<String> paragraphs = paras.stream().map(NovelParagraph::getContent).toList();

        ChapterVO vo = new ChapterVO();
        vo.setId(chapter.getId());
        vo.setNovelId(chapter.getNovelId());
        vo.setChapterNo(chapter.getChapterNo());
        vo.setTitle(chapter.getTitle());
        vo.setChars(chapter.getChars());
        vo.setParagraphs(paragraphs);
        // 上下章服务端计算
        NovelChapter prev = chapterMapper.selectPrev(chapter.getNovelId(), chapter.getChapterNo());
        NovelChapter next = chapterMapper.selectNext(chapter.getNovelId(), chapter.getChapterNo());
        vo.setPrevChapterId(prev == null ? null : prev.getId());
        vo.setNextChapterId(next == null ? null : next.getId());
        return vo;
    }

    /** 取阅读进度 */
    public NovelReadProgress getProgress(Long userId, Long novelId) {
        return progressMapper.selectOne(new LambdaQueryWrapper<NovelReadProgress>()
                .eq(NovelReadProgress::getUserId, userId)
                .eq(NovelReadProgress::getNovelId, novelId));
    }

    /** 保存/更新阅读进度（upsert） */
    @Transactional
    public void saveProgress(Long userId, Long novelId, Long chapterId,
                             Integer charOffset, BigDecimal percent) {
        NovelChapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !chapter.getNovelId().equals(novelId)) {
            throw new BusinessException("章节与小说不匹配");
        }
        NovelReadProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<NovelReadProgress>()
                .eq(NovelReadProgress::getUserId, userId)
                .eq(NovelReadProgress::getNovelId, novelId));
        if (progress == null) {
            progress = new NovelReadProgress();
            progress.setUserId(userId);
            progress.setNovelId(novelId);
        }
        progress.setChapterId(chapterId);
        progress.setCharOffset(charOffset == null ? 0 : charOffset);
        progress.setPercent(percent == null ? BigDecimal.ZERO : percent);
        progress.setUpdateTime(LocalDateTime.now());
        if (progress.getId() == null) {
            progressMapper.insert(progress);
        } else {
            progressMapper.updateById(progress);
        }
    }

    private NovelVO toVO(Novel n) {
        NovelVO vo = new NovelVO();
        vo.setId(n.getId());
        vo.setTitle(n.getTitle());
        vo.setAuthor(n.getAuthor());
        vo.setIntro(n.getIntro());
        vo.setCover(n.getCover());
        vo.setCategory(n.getCategory());
        vo.setStatus(n.getStatus());
        vo.setChapterCount(n.getChapterCount());
        vo.setTotalChars(n.getTotalChars());
        return vo;
    }

    // ==================== 管理端：生命周期操作 ====================

    private final NovelParserService parserService;
    private final com.japy.module.audit.service.AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    /** 上传并入库：解析 → 落盘 → 写三表 → 自动上架 */
    @Transactional
    public NovelVO upload(String title, String author, String category, String intro,
                          org.springframework.web.multipart.MultipartFile file) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("书名不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择 txt 文件");
        }
        NovelParserService.ParseResult parsed;
        try {
            parsed = parserService.parse(file);
        } catch (java.io.IOException e) {
            throw new BusinessException("文件解析失败: " + e.getMessage());
        }
        if (parsed.getChapters().isEmpty()) {
            throw new BusinessException("文件中未解析到任何内容");
        }

        // 1. 先插小说（拿 id 供目录命名 + 落盘）
        Novel novel = new Novel();
        novel.setTitle(title.trim());
        novel.setAuthor(author == null || author.isBlank() ? "佚名" : author.trim());
        novel.setCategory(category == null || category.isBlank() ? "其他" : category.trim());
        novel.setIntro(intro);
        novel.setStatus(2);          // 草稿：解析落盘中
        novel.setChapterCount(parsed.getChapters().size());        novel.setTotalChars(parsed.getTotalChars());
        novel.setDelFlag(0);
        novelMapper.insert(novel);

        // 2. 落盘（章节文件 + meta.json）
        try {
            java.nio.file.Path dir = parserService.saveFiles(novel.getId(), novel.getTitle(), file, parsed);
            novel.setFilePath(dir.toString().replace('\\', '/'));
        } catch (java.io.IOException e) {
            log.warn("文件落盘失败（仅影响元数据）: {}", e.getMessage());
        }

        // 3. 写章节 + 段落
        for (int i = 0; i < parsed.getChapters().size(); i++) {
            NovelParserService.Chapter ch = parsed.getChapters().get(i);
            NovelChapter chapter = new NovelChapter();
            chapter.setNovelId(novel.getId());
            chapter.setChapterNo(i + 1);
            chapter.setTitle(ch.getTitle());
            chapter.setChars(ch.getChars());
            chapter.setParagraphCount(ch.getParagraphs().size());
            chapterMapper.insert(chapter);
            for (int j = 0; j < ch.getParagraphs().size(); j++) {
                NovelParagraph para = new NovelParagraph();
                para.setNovelId(novel.getId());
                para.setChapterNo(i + 1);
                para.setParaSeq(j + 1);
                para.setContent(ch.getParagraphs().get(j));
                para.setChars(ch.getParagraphs().get(j).length());
                paragraphMapper.insert(para);
            }
        }

        // 4. 自动上架
        novel.setStatus(0);
        novelMapper.updateById(novel);

        // 5. 内容扫描留痕（audit 域：无命中 PASS，有命中 PENDING，小说保持上架）
        auditService.scanAndRecord(novel.getId(), novel.getTitle(), novel.getIntro(), "UPLOAD");

        // 6. 触发 RAG 索引同步（异步事件，不阻塞上传）
        eventPublisher.publishEvent(new com.japy.module.rag.event.NovelUploadedEvent(novel.getId()));
        return toVO(novel);
    }

    /** 状态流转（0上架 1下架 2草稿） */
    public void changeStatus(Long novelId, int target) {
        if (target != 0 && target != 1 && target != 2) {
            throw new BusinessException("非法状态");
        }
        Novel novel = novelMapper.selectById(novelId);
        if (novel == null) {
            throw new BusinessException("小说不存在");
        }
        novel.setStatus(target);
        novelMapper.updateById(novel);
    }

    /** 逻辑删除（@TableLogic 自动过滤） */
    public void delete(Long novelId) {
        Novel novel = novelMapper.selectById(novelId);
        if (novel == null) {
            throw new BusinessException("小说不存在");
        }
        novelMapper.deleteById(novelId);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NovelService.class);
}
