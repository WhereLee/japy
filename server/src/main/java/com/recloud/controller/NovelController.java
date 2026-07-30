package com.recloud.controller;

import com.recloud.common.annotation.Log;
import com.recloud.common.result.R;
import com.recloud.entity.Chapter;
import com.recloud.entity.Novel;
import com.recloud.service.NovelService;
import com.recloud.vo.ChapterVO;
import com.recloud.vo.NovelVO;
import com.recloud.vo.VOConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "小说管理", description = "小说列表/详情/章节内容/导入")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NovelController {

    private final NovelService novelService;

    @Operation(summary = "小说列表")
    @GetMapping("/novels")
    public R<List<NovelVO>> listNovels() {
        return R.ok(VOConverter.toNovelVOList(novelService.listNovels()));
    }

    @Operation(summary = "小说详情 + 章节目录")
    @GetMapping("/novels/{id}")
    public R<Map<String, Object>> getNovelDetail(@PathVariable Long id) {
        Novel novel = novelService.getNovel(id);
        List<Chapter> chapters = novelService.listChapters(id);
        return R.ok(Map.of(
                "novel", VOConverter.toVO(novel),
                "chapters", VOConverter.toChapterVOList(chapters)
        ));
    }

    @Operation(summary = "章节内容")
    @GetMapping("/chapters/{id}")
    public R<ChapterVO> getChapter(@PathVariable Long id) {
        return R.ok(VOConverter.toVO(novelService.getChapter(id)));
    }

    @Operation(summary = "手动触发小说导入")
    @PostMapping("/novels/import")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "小说", operation = "导入小说")
    public R<Map<String, String>> importNovels() {
        novelService.scanAndImport();
        return R.ok(Map.of("message", "扫描完成"));
    }
}
