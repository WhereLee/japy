package com.japy.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.module.system.entity.SysDictData;
import com.japy.module.system.mapper.SysDictDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 字典数据缓存（Redis，10 分钟 TTL）：高频只读数据避免每次查库；
 * 增删改后主动失效（Cache Aside 模式）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictCacheService {

    private static final String KEY = "sys:dict:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final SysDictDataMapper dictDataMapper;
    private final ObjectMapper objectMapper;

    public List<SysDictData> getData(String dictType) {
        String cached = redis.opsForValue().get(KEY + dictType);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<SysDictData>>() {});
            } catch (Exception e) {
                log.warn("字典缓存反序列化失败，回源查询: {}", e.getMessage());
            }
        }
        List<SysDictData> list = dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, 0)
                .orderByAsc(SysDictData::getSort));
        try {
            redis.opsForValue().set(KEY + dictType, objectMapper.writeValueAsString(list), TTL);
        } catch (Exception e) {
            log.warn("字典缓存写入失败: {}", e.getMessage());
        }
        return list;
    }

    /** 失效某类字典 */
    public void evict(String dictType) {
        redis.delete(KEY + dictType);
    }

    /** 失效全部字典 */
    public void evictAll() {
        try {
            var keys = redis.keys(KEY + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }
}
