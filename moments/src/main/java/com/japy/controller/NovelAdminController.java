package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.AdminLog;
import com.japy.common.PageParams;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.entity.Novel;
import com.japy.entity.NovelChapter;
import com.japy.mapper.NovelChapterMapper;
import com.japy.mapper.NovelMapper;
import com.japy.service.NovelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端：小说上传与查询。
 */
@RestController
@RequestMapping("/api/admin/novels")
@RequiredArgsConstructor
public class NovelAdminController {

    private final NovelService novelService;
    private final NovelMapper novelMapper;
    private final NovelChapterMapper chapterMapper;

    /**
     * 上传 txt 并入库：同步完成 章节检测 → 统计 → 落盘 → 数据库，返回完整结果。
     */
    @PostMapping("/upload")
    @AdminLog(action = "upload_novel")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(required = false) String author) {
        return R.ok(novelService.upload(file, author));
    }

    /** 小说列表（分页 + 关键词） */
    @GetMapping
    public R<PageResult<Novel>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Novel> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(Novel::getTitle, keyword);
        }
        w.orderByDesc(Novel::getCreatedAt);
        Page<Novel> result = novelMapper.selectPage(new Page<>(PageParams.page(page), PageParams.size(size)), w);
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    /** 小说详情（含章节清单与统计） */
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Novel novel = novelMapper.selectById(id);
        if (novel == null) return R.fail("小说不存在");

        List<NovelChapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<NovelChapter>()
                        .eq(NovelChapter::getNovelId, id)
                        .orderByAsc(NovelChapter::getChapterNo));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", novel.getId());
        data.put("title", novel.getTitle());
        data.put("author", novel.getAuthor());
        data.put("status", novel.getStatus());
        data.put("chapterCount", novel.getChapterCount());
        data.put("paragraphCount", novel.getParagraphCount());
        data.put("totalChars", novel.getTotalChars());
        data.put("sourceName", novel.getSourceName());
        data.put("sourceSize", novel.getSourceSize());
        data.put("sourceEncoding", novel.getSourceEncoding());
        data.put("dirPath", novel.getDirPath());
        data.put("createdAt", novel.getCreatedAt());
        data.put("chapters", chapters);
        return R.ok(data);
    }

    /** 删除小说（数据库 + 落盘目录） */
    @DeleteMapping("/{id}")
    @AdminLog(action = "delete_novel")
    public R<Void> delete(@PathVariable Long id) {
        if (!novelService.delete(id)) {
            return R.fail("小说不存在");
        }
        return R.ok();
    }
}
