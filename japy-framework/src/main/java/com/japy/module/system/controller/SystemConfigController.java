package com.japy.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.aspect.OperLog;
import com.japy.common.BusinessException;
import com.japy.common.R;
import com.japy.module.system.entity.SysConfig;
import com.japy.module.system.entity.SysDictData;
import com.japy.module.system.entity.SysDictType;
import com.japy.module.system.mapper.SysConfigMapper;
import com.japy.module.system.mapper.SysDictDataMapper;
import com.japy.module.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端：字典管理 / 参数管理
 */
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;
    private final SysConfigMapper configMapper;

    // ==================== 字典管理 ====================

    @GetMapping("/dict/type/list")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public R<List<SysDictType>> dictTypeList() {
        return R.ok(dictTypeMapper.selectList(new LambdaQueryWrapper<SysDictType>().orderByAsc(SysDictType::getId)));
    }

    @GetMapping("/dict/data/{dictType}")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public R<List<SysDictData>> dictData(@PathVariable String dictType) {
        return R.ok(dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, 0)
                .orderByAsc(SysDictData::getSort)));
    }

    @PostMapping("/dict/type")
    @PreAuthorize("hasAuthority('system:dict:add')")
    @OperLog(title = "字典管理", businessType = 1)
    public R<Void> addDictType(@RequestBody SysDictType type) {
        dictTypeMapper.insert(type);
        return R.ok();
    }

    @PostMapping("/dict/data")
    @PreAuthorize("hasAuthority('system:dict:add')")
    @OperLog(title = "字典管理", businessType = 1)
    public R<Void> addDictData(@RequestBody SysDictData data) {
        dictDataMapper.insert(data);
        return R.ok();
    }

    @PutMapping("/dict/data")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @OperLog(title = "字典管理", businessType = 2)
    public R<Void> editDictData(@RequestBody SysDictData data) {
        dictDataMapper.updateById(data);
        return R.ok();
    }

    @DeleteMapping("/dict/data/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @OperLog(title = "字典管理", businessType = 3)
    public R<Void> deleteDictData(@PathVariable Long id) {
        dictDataMapper.deleteById(id);
        return R.ok();
    }

    // ==================== 参数管理 ====================

    @GetMapping("/config/list")
    @PreAuthorize("hasAuthority('system:config:list')")
    public R<List<SysConfig>> configList() {
        return R.ok(configMapper.selectList(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId)));
    }

    @PostMapping("/config")
    @PreAuthorize("hasAuthority('system:config:add')")
    @OperLog(title = "参数管理", businessType = 1)
    public R<Void> addConfig(@RequestBody SysConfig config) {
        configMapper.insert(config);
        return R.ok();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('system:config:edit')")
    @OperLog(title = "参数管理", businessType = 2)
    public R<Void> editConfig(@RequestBody SysConfig config) {
        configMapper.updateById(config);
        return R.ok();
    }

    @DeleteMapping("/config/{id}")
    @PreAuthorize("hasAuthority('system:config:delete')")
    @OperLog(title = "参数管理", businessType = 3)
    public R<Void> deleteConfig(@PathVariable Long id) {
        configMapper.deleteById(id);
        return R.ok();
    }
}
