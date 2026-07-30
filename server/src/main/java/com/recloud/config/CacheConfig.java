package com.recloud.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 * <p>
 * 两级缓存架构：
 * - L1 Caffeine（本地缓存）：章节内容，热点数据
 *   - refreshAfterWrite=50s：异步刷新，用户始终命中缓存（可能旧10s），不阻塞
 *   - expireAfterAccess=10min：冷数据自动淘汰
 *   - maximumSize=500（最多缓存 500 个章节）
 * - L2 Redis（分布式缓存）：批注列表、用户信息，300s 过期
 * <p>
 * 防缓存击穿设计：
 * - Caffeine refreshAfterWrite：异步刷新，只有一个线程触发 DB 查询，其余命中旧值
 * - @Cacheable(sync=true)：缓存 miss 时只有一个线程去 DB 加载，其余等待
 * - Redis Cache-Aside 分布式锁：批注缓存 miss 时加锁，防止并发穿透
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Caffeine 本地缓存管理器
     * <p>
     * 防缓存击穿设计：
     * - @Cacheable(sync=true)：缓存 miss 时只有一个线程去 DB 加载，其余等待
     * - expireAfterWrite=60s：过期后下一个请求触发重新加载（sync=true 保证单线程）
     * - expireAfterAccess=10min：冷数据自动淘汰
     * <p>
     * 注意：不能使用 refreshAfterWrite，因为它需要 LoadingCache，
     * 而 Spring 的 CaffeineCacheManager 懒加载创建的是非 LoadingCache。
     * sync=true 已经提供了防击穿保护（单线程加载 + 其余等待）。
     */
    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)   // 60s 后过期，sync=true 保证单线程重新加载
                .expireAfterAccess(10, TimeUnit.MINUTES)  // 10min 无访问则淘汰
                .maximumSize(500)
                .recordStats()
        );
        // 不调用 setCacheNames()：让缓存在第一次 @Cacheable 访问时懒加载创建
        return cacheManager;
    }
}
