package com.japy.module.novel.controller;

import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.module.novel.service.NovelService;
import com.japy.module.novel.vo.NovelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 小说管理接口（管理端）：上传 / 列表 / 状态流转 / 删除。
 * 生命周期：草稿(2) → 上传完成自动连载(0) → 完结(1) / 下架(3) / 删除(逻辑)。
 */
@RestController
@RequestMapping("/admin/novel")
@RequiredArgsConstructor
public class AdminNovelController {

    private final NovelService novelService;

    /** 上传 txt 并入库（multipart: file + 元信息表单字段） */
    @PostMapping("/upload")
    @PreAuthorize("@ss.hasPermi('novel:upload')")
    @com.japy.aspect.OperLog(title = "小说管理", businessType = 1)
    public R<NovelVO> upload(@RequestParam("file") MultipartFile file,
                             @RequestParam("title") String title,
                             @RequestParam(required = false) String author,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String intro) {
        return R.ok(novelService.upload(title, author, category, intro, file));
    }

    /** 管理端列表（含草稿/下架，不含逻辑删除） */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('novel:list')")
    public R<PageResult<NovelVO>> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String keyword) {
        return R.ok(novelService.adminList(page, size, keyword));
    }

    /** 状态流转：0连载 1完结 2草稿 3下架 */
    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermi('novel:status')")
    @com.japy.aspect.OperLog(title = "小说管理", businessType = 2)
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam int status) {
        novelService.changeStatus(id, status);
        return R.ok();
    }

    /** 逻辑删除 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('novel:delete')")
    @com.japy.aspect.OperLog(title = "小说管理", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        novelService.delete(id);
        return R.ok();
    }
}
