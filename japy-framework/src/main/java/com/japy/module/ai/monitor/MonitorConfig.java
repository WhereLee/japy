package com.japy.module.ai.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.module.system.entity.SysConfig;
import com.japy.module.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 检测器阈值配置读取（sys_config，管理员可在参数管理调整）。
 * 不缓存：检测频率低（默认 30 分钟一次），直读数据库保证调整即时生效。
 */
@Component
@RequiredArgsConstructor
public class MonitorConfig {

    private final SysConfigMapper configMapper;

    public int getInt(String key, int def) {
        String v = getStr(key, null);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public double getDouble(String key, double def) {
        String v = getStr(key, null);
        if (v == null) {
            return def;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public String getStr(String key, String def) {
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key).last("LIMIT 1"));
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return def;
        }
        return config.getConfigValue();
    }
}
