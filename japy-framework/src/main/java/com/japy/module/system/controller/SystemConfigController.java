package com.japy.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.aspect.OperLog;
import com.japy.common.BusinessException;
import com.japy.common.R;
import com.japy.module.system.dto.AdminDtos;
import com.japy.module.system.entity.SysConfig;
import com.japy.module.system.entity.SysDictData;
import com.japy.module.system.entity.SysDictType;
import com.japy.module.system.mapper.SysConfigMapper;
import com.japy.module.system.mapper.SysDictDataMapper;
import com.japy.module.system.mapper.SysDictTypeMapper;
import com.japy.module.system.service.DictCacheService;
import jakarta.validation.Valid;
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
    private final DictCacheService dictCacheService;

    // ==================== 字典管理 ====================

    @GetMapping("/dict/type/list")
    @PreAuthorize("@ss.hasPermi('system:dict:list')")
    public R<List<SysDictType>> dictTypeList() {
        return R.ok(dictTypeMapper.selectList(new LambdaQueryWrapper<SysDictType>().orderByAsc(SysDictType::getId)));
    }

    @GetMapping("/dict/data/{dictType}")
    @PreAuthorize("@ss.hasPermi('system:dict:list')")
    public R<List<SysDictData>> dictData(@PathVariable String dictType) {
        return R.ok(dictCacheService.getData(dictType));
    }

    @PostMapping("/dict/type")
    @PreAuthorize("@ss.hasPermi('system:dict:add')")
    @OperLog(title = "字典管理", businessType = 1)
    public R<Void> addDictType(@Valid @RequestBody AdminDtos.DictTypeDTO dto) {
        SysDictType type = new SysDictType();
        type.setDictName(dto.getDictName());
        type.setDictType(dto.getDictType());
        type.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        type.setRemark(dto.getRemark());
        dictTypeMapper.insert(type);
        dictCacheService.evictAll();
        return R.ok();
    }

    @PostMapping("/dict/data")
    @PreAuthorize("@ss.hasPermi('system:dict:add')")
    @OperLog(title = "字典管理", businessType = 1)
    public R<Void> addDictData(@Valid @RequestBody AdminDtos.DictDataDTO dto) {
        SysDictData data = new SysDictData();
        data.setDictType(dto.getDictType());
        data.setDictLabel(dto.getDictLabel());
        data.setDictValue(dto.getDictValue());
        data.setSort(dto.getSort() == null ? 0 : dto.getSort());
        data.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        data.setRemark(dto.getRemark());
        dictDataMapper.insert(data);
        dictCacheService.evict(data.getDictType());
        return R.ok();
    }

    @PutMapping("/dict/data")
    @PreAuthorize("@ss.hasPermi('system:dict:edit')")
    @OperLog(title = "字典管理", businessType = 2)
    public R<Void> editDictData(@Valid @RequestBody AdminDtos.DictDataDTO dto) {
        SysDictData data = new SysDictData();
        data.setId(dto.getId());
        data.setDictLabel(dto.getDictLabel());
        data.setDictValue(dto.getDictValue());
        data.setSort(dto.getSort());
        data.setStatus(dto.getStatus());
        data.setRemark(dto.getRemark());
        dictDataMapper.updateById(data);
        dictCacheService.evict(data.getDictType());
        return R.ok();
    }

    @DeleteMapping("/dict/data/{id}")
    @PreAuthorize("@ss.hasPermi('system:dict:delete')")
    @OperLog(title = "字典管理", businessType = 3)
    public R<Void> deleteDictData(@PathVariable Long id) {
        SysDictData data = dictDataMapper.selectById(id);
        if (data != null) {
            dictDataMapper.deleteById(id);
            dictCacheService.evict(data.getDictType());
        }
        return R.ok();
    }

    // ==================== 参数管理 ====================

    @GetMapping("/config/list")
    @PreAuthorize("@ss.hasPermi('system:config:list')")
    public R<List<SysConfig>> configList() {
        return R.ok(configMapper.selectList(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId)));
    }

    @PostMapping("/config")
    @PreAuthorize("@ss.hasPermi('system:config:add')")
    @OperLog(title = "参数管理", businessType = 1)
    public R<Void> addConfig(@Valid @RequestBody AdminDtos.ConfigDTO dto) {
        SysConfig config = new SysConfig();
        config.setConfigName(dto.getConfigName());
        config.setConfigKey(dto.getConfigKey());
        config.setConfigValue(dto.getConfigValue());
        config.setRemark(dto.getRemark());
        configMapper.insert(config);
        return R.ok();
    }

    @PutMapping("/config")
    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    @OperLog(title = "参数管理", businessType = 2)
    public R<Void> editConfig(@Valid @RequestBody AdminDtos.ConfigDTO dto) {
        SysConfig config = new SysConfig();
        config.setId(dto.getId());
        config.setConfigName(dto.getConfigName());
        config.setConfigKey(dto.getConfigKey());
        config.setConfigValue(dto.getConfigValue());
        config.setRemark(dto.getRemark());
        configMapper.updateById(config);
        return R.ok();
    }

    @DeleteMapping("/config/{id}")
    @PreAuthorize("@ss.hasPermi('system:config:delete')")
    @OperLog(title = "参数管理", businessType = 3)
    public R<Void> deleteConfig(@PathVariable Long id) {
        configMapper.deleteById(id);
        return R.ok();
    }
}
