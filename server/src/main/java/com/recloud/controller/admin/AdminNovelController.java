package com.recloud.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recloud.common.annotation.Log;
import com.recloud.common.result.R;
import com.recloud.common.result.ResultCode;
import com.recloud.entity.Novel;
import com.recloud.service.NovelService;
import com.recloud.vo.NovelVO;
import com.recloud.vo.VOConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@Tag(name = "管理端-小说管理", description = "小说列表/导入/删除")
@RestController
@RequestMapping("/admin/novels")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNovelController {

    private final NovelService novelService;

    @Operation(summary = "小说列表（分页）")
    @GetMapping
    public R<IPage<NovelVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        IPage<Novel> novelPage = novelService.listNovels(page, size);
        // Entity → VO 转换
        IPage<NovelVO> voPage = new Page<>(page, size, novelPage.getTotal());
        voPage.setRecords(novelPage.getRecords().stream()
                .map(VOConverter::toVO)
                .collect(Collectors.toList()));
        return R.ok(voPage);
    }

    @Operation(summary = "触发小说导入")
    @PostMapping("/import")
    @Log(module = "小说管理", operation = "触发导入")
    public R<String> importNovels() {
        novelService.scanAndImport();
        return R.ok("导入完成");
    }

    @Operation(summary = "删除小说")
    @DeleteMapping("/{id}")
    @Log(module = "小说管理", operation = "删除小说")
    public R<String> delete(@PathVariable Long id) {
        boolean success = novelService.adminDeleteNovel(id);
        return success ? R.ok("删除成功") : R.fail(ResultCode.NOVEL_NOT_FOUND);
    }
}
