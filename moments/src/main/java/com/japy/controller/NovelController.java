package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.PageParams;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.entity.Novel;
import com.japy.mapper.NovelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端：小说列表（说说大厅的导航底座）。
 * GET 公开可看（与时间线一致）。
 */
@RestController
@RequestMapping("/api/novels")
@RequiredArgsConstructor
public class NovelController {

    private final NovelMapper novelMapper;

    /** 已入库小说列表（分页） */
    @GetMapping
    public R<PageResult<Novel>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<Novel> result = novelMapper.selectPage(new Page<>(PageParams.page(page), PageParams.size(size)),
                new LambdaQueryWrapper<Novel>()
                        .eq(Novel::getStatus, 1)
                        .orderByDesc(Novel::getCreatedAt));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }
}
