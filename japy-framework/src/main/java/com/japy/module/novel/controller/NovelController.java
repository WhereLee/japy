package com.japy.module.novel.controller;

import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.SecurityUtils;
import com.japy.module.novel.entity.NovelChapter;
import com.japy.module.novel.entity.NovelReadProgress;
import com.japy.module.novel.service.NovelService;
import com.japy.module.novel.vo.ChapterVO;
import com.japy.module.novel.vo.NovelVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 小说阅读接口（用户端）：列表 / 详情 / 章节 / 阅读进度。
 * 需登录（SecurityConfig anyRequest().authenticated() 兜底）。
 */
@RestController
@RequestMapping("/novel")
@RequiredArgsConstructor
public class NovelController {

    private final NovelService novelService;

    /** 小说列表 */
    @GetMapping("/list")
    public R<PageResult<NovelVO>> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String category) {
        return R.ok(novelService.listNovels(page, size, keyword, category));
    }

    /** 小说详情 */
    @GetMapping("/{novelId}")
    public R<NovelVO> detail(@PathVariable Long novelId) {
        return R.ok(novelService.detail(novelId));
    }

    /** 章节列表（懒加载分页） */
    @GetMapping("/{novelId}/chapters")
    public R<PageResult<NovelChapter>> chapters(@PathVariable Long novelId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return R.ok(novelService.listChapters(novelId, page, size));
    }

    /** 章节内容（含上下章 id） */
    @GetMapping("/chapter/{chapterId}")
    public R<ChapterVO> chapter(@PathVariable Long chapterId) {
        return R.ok(novelService.chapterContent(chapterId));
    }

    /** 我的阅读进度 */
    @GetMapping("/{novelId}/progress")
    public R<NovelReadProgress> progress(@PathVariable Long novelId) {
        return R.ok(novelService.getProgress(SecurityUtils.userId(), novelId));
    }

    /** 保存阅读进度 */
    @PutMapping("/{novelId}/progress")
    public R<Void> saveProgress(@PathVariable Long novelId, @Valid @RequestBody ProgressDTO dto) {
        novelService.saveProgress(SecurityUtils.userId(), novelId,
                dto.getChapterId(), dto.getCharOffset(), dto.getPercent());
        return R.ok();
    }

    @Data
    public static class ProgressDTO {
        @NotNull(message = "章节不能为空")
        private Long chapterId;
        @Min(0)
        private Integer charOffset;
        @Min(0)
        @Max(100)
        private BigDecimal percent;
    }
}
