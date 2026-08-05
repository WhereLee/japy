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

    /** 小说列表（分页 + 可选关键词/分类） */
    public PageResult<NovelVO> listNovels(int page, int size, String keyword, String category) {
        Page<Novel> p = novelMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Novel>()
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(Novel::getTitle, keyword).or().like(Novel::getAuthor, keyword))
                        .eq(category != null && !category.isBlank(), Novel::getCategory, category)
                        .orderByDesc(Novel::getUpdateTime));
        List<NovelVO> list = p.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, p.getTotal(), page, size);
    }

    /** 小说详情 */
    public NovelVO detail(Long novelId) {
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
}
