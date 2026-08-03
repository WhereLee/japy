package com.japy.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.aspect.OperLog;
import com.japy.common.BusinessException;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.module.system.dto.AdminDtos;
import com.japy.module.system.entity.SysNotice;
import com.japy.module.system.mapper.SysNoticeMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端：公告管理
 */
@RestController
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class SystemNoticeController {

    private final SysNoticeMapper noticeMapper;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:notice:list')")
    public R<PageResult<SysNotice>> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        Page<SysNotice> p = noticeMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysNotice>().orderByDesc(SysNotice::getId));
        return R.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:notice:add')")
    @OperLog(title = "公告管理", businessType = 1)
    public R<Void> add(@Valid @RequestBody AdminDtos.NoticeDTO dto) {
        SysNotice notice = new SysNotice();
        notice.setNoticeTitle(dto.getNoticeTitle());
        notice.setNoticeType(dto.getNoticeType());
        notice.setNoticeContent(dto.getNoticeContent());
        notice.setStatus(0);
        noticeMapper.insert(notice);
        return R.ok();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:notice:edit')")
    @OperLog(title = "公告管理", businessType = 2)
    public R<Void> edit(@Valid @RequestBody AdminDtos.NoticeDTO dto) {
        SysNotice notice = new SysNotice();
        notice.setId(dto.getId());
        notice.setNoticeTitle(dto.getNoticeTitle());
        notice.setNoticeType(dto.getNoticeType());
        notice.setNoticeContent(dto.getNoticeContent());
        notice.setStatus(dto.getStatus());
        noticeMapper.updateById(notice);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:delete')")
    @OperLog(title = "公告管理", businessType = 3)
    public R<Void> delete(@PathVariable Long id) {
        noticeMapper.deleteById(id);
        return R.ok();
    }
}
