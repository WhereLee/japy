package com.japy.service;

import com.japy.entity.SensitiveWord;
import com.japy.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private final SensitiveWordMapper wordMapper;

    /** 内存缓存，避免每次全表扫描 */
    private volatile List<String> cachedWords = null;
    private volatile long cacheTime = 0;
    private static final long CACHE_TTL_MS = 60_000; // 60秒过期

    private List<String> getWords() {
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (cachedWords == null || now - cacheTime > CACHE_TTL_MS) {
                cachedWords = new CopyOnWriteArrayList<>(
                        wordMapper.selectList(null).stream()
                                .map(w -> w.getWord().toLowerCase())
                                .toList());
                cacheTime = now;
            }
            return cachedWords;
        }
    }

    /** 检查文本是否包含敏感词，返回命中的词或 null */
    public String check(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase();
        for (String w : getWords()) {
            if (lower.contains(w)) {
                return w;
            }
        }
        return null;
    }

    /** 管理员增删敏感词时调用，使缓存失效（与 getWords 同步，避免并发竞态） */
    public synchronized void invalidateCache() {
        cachedWords = null;
        cacheTime = 0;
    }
}
