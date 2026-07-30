package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recloud.entity.Annotation;
import com.recloud.mapper.AnnotationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存一致性校验服务
 * <p>
 * 设计思路：
 * 定时对比 Redis 中的点赞数据与 DB 中的 like_count 是否一致。
 * 发现不一致时记录告警日志，并自动修复（以 Redis 为准）。
 * <p>
 * 为什么以 Redis 为准？
 * - Redis Set 是点赞操作的原子写入源（SADD/SREM）
 * - DB like_count 是定时回写的冗余字段
 * - 如果两者不一致，说明回写延迟或失败，Redis 的数据更新更准确
 * <p>
 * 校验范围：
 * - 优先校验 dirty set 中的批注（有点赞变更但未同步的）
 * - 随机抽检 50 条近期批注（防止 dirty set 被清空后的一致性问题）
 * <p>
 * 面试价值：
 * - 展示"最终一致性"思维：分布式系统中缓存和 DB 不可能强一致
 * - 展示"监控 + 自愈"能力：发现不一致 → 告警 → 自动修复
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheConsistencyService {

    private final AnnotationMapper annotationMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String LIKE_STATUS_PREFIX = "like:status:";
    private static final String LIKE_DIRTY_KEY = "like:dirty";
    /** 告警阈值：差异超过此值时记录 WARN 级别日志 */
    private static final int ALERT_THRESHOLD = 5;
    /** 随机抽检的批注数量 */
    private static final int SPOT_CHECK_COUNT = 50;
    /** 告警冷却 key 前缀（防止同一批注重复告警） */
    private static final String ALERT_COOLDOWN_PREFIX = "cache:alert:cooldown:";

    /**
     * 定时任务：每小时执行一次缓存一致性校验
     * <p>
     * 校验流程：
     * 1. 校验 dirty set 中的批注（有变更但未同步的）
     * 2. 随机抽检近期批注
     * 3. 发现不一致 → 告警 + 自动修复
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void verifyCacheConsistency() {
        log.info("开始缓存一致性校验...");
        int totalChecked = 0;
        int inconsistencyCount = 0;

        // 第一步：校验 dirty set 中的批注
        try {
            Set<String> dirtyIds = redisTemplate.opsForSet().members(LIKE_DIRTY_KEY);
            if (dirtyIds != null && !dirtyIds.isEmpty()) {
                for (String idStr : dirtyIds) {
                    try {
                        Long annotationId = Long.parseLong(idStr);
                        totalChecked++;
                        if (checkAndFix(annotationId)) {
                            inconsistencyCount++;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("校验 dirty set 失败: {}", e.getMessage());
        }

        // 第二步：随机抽检近期批注
        try {
            List<Annotation> recentAnnotations = annotationMapper.selectList(
                    new LambdaQueryWrapper<Annotation>()
                            .ge(Annotation::getCreatedAt,
                                    java.time.LocalDateTime.now().minusDays(7))
                            .gt(Annotation::getLikeCount, 0)
                            .orderByDesc(Annotation::getCreatedAt)
                            .last("LIMIT " + SPOT_CHECK_COUNT)
            );

            for (Annotation annotation : recentAnnotations) {
                totalChecked++;
                if (checkAndFix(annotation.getId())) {
                    inconsistencyCount++;
                }
            }
        } catch (Exception e) {
            log.warn("随机抽检失败: {}", e.getMessage());
        }

        log.info("缓存一致性校验完成：检查 {} 条，不一致 {} 条",
                totalChecked, inconsistencyCount);
    }

    /**
     * 检查单条批注的缓存一致性，不一致时自动修复
     *
     * @return true = 发现不一致，false = 一致
     */
    private boolean checkAndFix(Long annotationId) {
        try {
            String statusKey = LIKE_STATUS_PREFIX + annotationId;

            // 从 Redis SCARD 获取计数
            Long redisCount = redisTemplate.opsForSet().size(statusKey);
            if (redisCount == null) redisCount = 0L;

            // 从 DB 获取 like_count
            Annotation annotation = annotationMapper.selectById(annotationId);
            if (annotation == null) return false;

            int dbCount = annotation.getLikeCount() != null ? annotation.getLikeCount() : 0;
            int diff = Math.abs((int) (redisCount - dbCount));

            if (diff == 0) return false;

            // 发现不一致
            if (diff >= ALERT_THRESHOLD) {
                // 告警冷却：同一批注 1 小时内只告警一次
                String cooldownKey = ALERT_COOLDOWN_PREFIX + annotationId;
                Boolean needAlert = redisTemplate.opsForValue()
                        .setIfAbsent(cooldownKey, "1", 1, TimeUnit.HOURS);
                if (Boolean.TRUE.equals(needAlert)) {
                    log.warn("【缓存一致性告警】annotationId={}, redisCount={}, dbCount={}, diff={}",
                            annotationId, redisCount, dbCount, diff);
                }
            } else {
                log.debug("缓存轻微不一致: annotationId={}, redisCount={}, dbCount={}, diff={}",
                        annotationId, redisCount, dbCount, diff);
            }

            // 自动修复：以 Redis 为准更新 DB
            annotationMapper.updateLikeCountDirect(annotationId, redisCount.intValue());
            log.info("缓存一致性自动修复: annotationId={}, dbCount {} -> {}",
                    annotationId, dbCount, redisCount);

            return true;

        } catch (Exception e) {
            log.warn("检查/修复单条缓存一致性失败: annotationId={}, error={}",
                    annotationId, e.getMessage());
            return false;
        }
    }
}
