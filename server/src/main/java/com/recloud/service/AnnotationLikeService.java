package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recloud.entity.Annotation;
import com.recloud.entity.AnnotationLike;
import com.recloud.mapper.AnnotationLikeMapper;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.config.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 点赞服务（Redis 原子操作 + 异步回写 DB）
 * <p>
 * 高并发设计：
 * - 点赞状态：Redis Set（SADD/SREM 原子操作，O(1) 判断是否点赞）
 * - 点赞计数：SCARD 从 Set 直接获取，与状态天然一致
 * - 持久化：定时任务每 30s 批量回写 DB（like_count + annotation_like 表）
 * - 降级：Redis 不可用时直接走 DB，保证可用性
 * <p>
 * 对比原方案（delete + insert + updateLikeCount）：
 * - 原方案：3次DB操作/请求，快速双击可能状态不一致
 * - 新方案：1次Redis操作/请求，SADD/SREM 天然幂等，快速双击结果正确
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationLikeService {

    private final AnnotationLikeMapper likeMapper;
    private final AnnotationMapper annotationMapper;
    private final StringRedisTemplate redisTemplate;
    private final BusinessMetrics businessMetrics;
    private final NotificationService notificationService;

    private static final String LIKE_STATUS_PREFIX = "like:status:";
    private static final String LIKE_DIRTY_KEY = "like:dirty";
    private static final String ANNOTATION_CACHE_PREFIX = "annotation:chapter:";
    private static final long LIKE_STATUS_TTL_HOURS = 24;

    /**
     * 点赞/取消点赞（toggle）
     * <p>
     * Redis Set 原子操作：
     * - SADD 返回 1 = 新增成功（点赞）
     * - SADD 返回 0 = 已存在（需切换为 SREM 取消点赞）
     * <p>
     * 对比原方案的并发安全性：
     * - 原方案：delete + insert 两步操作，快速双击可能两次都删除成功
     * - 新方案：SADD/SREM 单次原子操作，快速双击结果正确（赞→取消→赞）
     *
     * @return { liked: boolean, likeCount: long }
     */
    public Map<String, Object> toggle(Long annotationId, Long userId) {
        String statusKey = LIKE_STATUS_PREFIX + annotationId;

        try {
            // 确保 Redis 可用（首次访问时从 DB 初始化 Set）
            ensureInitialized(annotationId, statusKey);

            // SADD 尝试添加，返回 1=新增成功，0=已存在
            Long added = redisTemplate.opsForSet().add(statusKey, String.valueOf(userId));

            boolean liked;
            if (added != null && added == 1L) {
                // 新增点赞成功
                liked = true;
                redisTemplate.expire(statusKey, LIKE_STATUS_TTL_HOURS, TimeUnit.HOURS);
            } else {
                // 已存在，取消点赞
                redisTemplate.opsForSet().remove(statusKey, String.valueOf(userId));
                liked = false;
            }

            // 标记该批注的计数需要同步到 DB
            redisTemplate.opsForSet().add(LIKE_DIRTY_KEY, String.valueOf(annotationId));

            // 从 Set 获取精确计数（SCARD 原子操作）
            Long count = redisTemplate.opsForSet().size(statusKey);
            businessMetrics.incrementLikeToggled();

            // 点赞成功时发送互动通知（异步，不阻塞主流程）
            if (liked) {
                trySendLikeNotification(annotationId, userId);
            }

            return Map.of("liked", liked, "likeCount", count != null ? count : 0L);

        } catch (Exception e) {
            // Redis 不可用，降级到 DB 操作
            log.warn("Redis 点赞操作失败，降级到DB: {}", e.getMessage());
            return toggleFallback(annotationId, userId);
        }
    }

    /**
     * 点赞成功后异步发送互动通知
     * <p>
     * 防骚扰策略：使用 Redis key 控制同一对 (annotationId, userId) 24h 内只通知一次
     */
    private void trySendLikeNotification(Long annotationId, Long userId) {
        try {
            String notifyKey = "like:notify:" + annotationId + ":" + userId;
            Boolean needNotify = redisTemplate.opsForValue()
                    .setIfAbsent(notifyKey, "1", 24, TimeUnit.HOURS);
            if (Boolean.TRUE.equals(needNotify)) {
                Annotation annotation = annotationMapper.selectById(annotationId);
                if (annotation != null) {
                    notificationService.sendLikeNotification(
                            annotation.getUserId(), userId, annotationId);
                }
            }
        } catch (Exception e) {
            log.warn("发送点赞通知失败: annotationId={}, error={}", annotationId, e.getMessage());
        }
    }

    /**
     * 确保 Redis Set 已初始化（从 DB 加载点赞用户）
     * <p>
     * 首次访问某个批注的点赞状态时，从 DB 加载所有点赞用户到 Redis Set。
     * 使用 SETNX 保证只有一个线程执行初始化。
     */
    private void ensureInitialized(Long annotationId, String statusKey) {
        String initKey = statusKey + ":init";
        Boolean needInit = redisTemplate.opsForValue().setIfAbsent(initKey, "1", 1, TimeUnit.HOURS);

        if (Boolean.TRUE.equals(needInit)) {
            // 从 DB 加载点赞用户到 Redis Set
            List<AnnotationLike> likes = likeMapper.selectList(
                    new LambdaQueryWrapper<AnnotationLike>()
                            .eq(AnnotationLike::getAnnotationId, annotationId)
            );
            if (!likes.isEmpty()) {
                String[] userIds = likes.stream()
                        .map(l -> String.valueOf(l.getUserId()))
                        .toArray(String[]::new);
                redisTemplate.opsForSet().add(statusKey, userIds);
            }
        }
    }

    /**
     * DB 降级方案（Redis 不可用时使用）
     */
    private Map<String, Object> toggleFallback(Long annotationId, Long userId) {
        int deleted = likeMapper.delete(
                new LambdaQueryWrapper<AnnotationLike>()
                        .eq(AnnotationLike::getAnnotationId, annotationId)
                        .eq(AnnotationLike::getUserId, userId)
        );

        boolean liked;
        if (deleted > 0) {
            annotationMapper.updateLikeCount(annotationId, -1);
            liked = false;
        } else {
            AnnotationLike like = new AnnotationLike();
            like.setAnnotationId(annotationId);
            like.setUserId(userId);
            likeMapper.insert(like);
            annotationMapper.updateLikeCount(annotationId, 1);
            liked = true;
        }

        long count = likeMapper.selectCount(
                new LambdaQueryWrapper<AnnotationLike>()
                        .eq(AnnotationLike::getAnnotationId, annotationId)
        );
        businessMetrics.incrementLikeToggled();
        return Map.of("liked", liked, "likeCount", count);
    }

    /**
     * 批量查询当前用户对多条批注的点赞状态
     * <p>
     * 优先从 Redis Set 查询（SISMEMBER O(1)），降级到 DB
     */
    public Set<Long> batchIsLiked(List<Long> annotationIds, Long userId) {
        if (annotationIds == null || annotationIds.isEmpty()) {
            return Collections.emptySet();
        }

        try {
            // 批量从 Redis 查询（Pipeline 或逐个 SISMEMBER）
            Set<Long> likedIds = new HashSet<>();
            for (Long annotationId : annotationIds) {
                String statusKey = LIKE_STATUS_PREFIX + annotationId;
                Boolean isMember = redisTemplate.opsForSet().isMember(statusKey, String.valueOf(userId));
                if (Boolean.TRUE.equals(isMember)) {
                    likedIds.add(annotationId);
                }
            }
            return likedIds;
        } catch (Exception e) {
            // Redis 不可用，降级到 DB
            log.warn("Redis 批量查询点赞状态失败，降级到DB: {}", e.getMessage());
            return likeMapper.selectList(
                    new LambdaQueryWrapper<AnnotationLike>()
                            .eq(AnnotationLike::getUserId, userId)
                            .in(AnnotationLike::getAnnotationId, annotationIds)
            ).stream().map(AnnotationLike::getAnnotationId).collect(Collectors.toSet());
        }
    }

    /**
     * 查询当前用户是否已赞（Redis SISMEMBER O(1)）
     */
    public boolean isLiked(Long annotationId, Long userId) {
        try {
            String statusKey = LIKE_STATUS_PREFIX + annotationId;
            Boolean isMember = redisTemplate.opsForSet().isMember(statusKey, String.valueOf(userId));
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            // 降级到 DB
            return likeMapper.selectCount(
                    new LambdaQueryWrapper<AnnotationLike>()
                            .eq(AnnotationLike::getAnnotationId, annotationId)
                            .eq(AnnotationLike::getUserId, userId)
            ) > 0;
        }
    }

    /**
     * 查询某条批注的点赞数（从 Redis SCARD 获取）
     */
    public long countByAnnotation(Long annotationId) {
        try {
            String statusKey = LIKE_STATUS_PREFIX + annotationId;
            Long count = redisTemplate.opsForSet().size(statusKey);
            return count != null ? count : 0;
        } catch (Exception e) {
            // 降级到 DB
            return likeMapper.selectCount(
                    new LambdaQueryWrapper<AnnotationLike>()
                            .eq(AnnotationLike::getAnnotationId, annotationId)
            );
        }
    }

    /**
     * 定时同步：将 Redis 点赞数据回写 DB
     * <p>
     * 每 30s 执行一次，处理“脏”批注（有点赞变更的）：
     * 1. 从 dirty set 获取有变更的批注 ID
     * 2. 用 SCARD 获取精确计数，更新 annotation.like_count
     * 3. 对比 DB 中的点赞记录，补齐缺失的 insert，清理已取消的脏数据
     * <p>
     * 竞态修复：
     * - 旧方案：处理完后 delete(LIKE_DIRTY_KEY) 清空整个 set，处理期间新写入的条目丢失
     * - 新方案：逐个 SREM 已处理的 ID，新写入的条目不受影响
     */
    @Scheduled(fixedDelay = 30_000)
    public void syncLikesToDb() {
        try {
            // 获取所有有变更的批注 ID
            Set<String> dirtyIds = redisTemplate.opsForSet().members(LIKE_DIRTY_KEY);
            if (dirtyIds == null || dirtyIds.isEmpty()) {
                return;
            }
    
            log.debug("开始同步点赞数据到DB，脏批注数: {}", dirtyIds.size());
    
            for (String idStr : dirtyIds) {
                try {
                    Long annotationId = Long.parseLong(idStr);
                    String statusKey = LIKE_STATUS_PREFIX + annotationId;
    
                    // 从 Redis Set 获取精确计数
                    Long redisCount = redisTemplate.opsForSet().size(statusKey);
                    if (redisCount == null) redisCount = 0L;
    
                    // 更新 DB 的 like_count
                    annotationMapper.updateLikeCountDirect(annotationId, redisCount.intValue());
    
                    // 同步 annotation_like 表（补齐 + 清理）
                    syncAnnotationLikeTable(annotationId, statusKey);

                    // 清除批注缓存（likeCount 是缓存对象的一部分）
                    evictAnnotationCache(annotationId);

                    // 逐个移除已处理的 dirty ID（不影响处理期间新写入的条目）
                    redisTemplate.opsForSet().remove(LIKE_DIRTY_KEY, idStr);
    
                } catch (Exception e) {
                    log.warn("同步单个批注点赞数据失败: annotationId={}, error={}", idStr, e.getMessage());
                }
            }
    
        } catch (Exception e) {
            log.error("点赞数据同步到DB失败: {}", e.getMessage());
        }
    }

    /**
     * 同步 annotation_like 表（双向同步：补齐 + 清理）
     * <p>
     * - 补齐：Redis 有但 DB 没有的 → insert
     * - 清理：DB 有但 Redis 没有的 → delete（用户已取消点赞）
     * <p>
     * 旧方案只补齐不清理，导致取消点赞后 DB 中残留脏记录，
     * like_count 与 annotation_like 表实际行数不一致。
     */
    private void syncAnnotationLikeTable(Long annotationId, String statusKey) {
        try {
            // 获取 Redis 中所有点赞用户
            Set<String> redisUserIds = redisTemplate.opsForSet().members(statusKey);
            if (redisUserIds == null) redisUserIds = Collections.emptySet();

            // 获取 DB 中已有的点赞记录
            Set<Long> dbUserIds = likeMapper.selectList(
                    new LambdaQueryWrapper<AnnotationLike>()
                            .eq(AnnotationLike::getAnnotationId, annotationId)
            ).stream().map(AnnotationLike::getUserId).collect(Collectors.toSet());

            // 补齐：Redis 有但 DB 没有的 → insert
            for (String userIdStr : redisUserIds) {
                Long userId = Long.parseLong(userIdStr);
                if (!dbUserIds.contains(userId)) {
                    AnnotationLike like = new AnnotationLike();
                    like.setAnnotationId(annotationId);
                    like.setUserId(userId);
                    likeMapper.insert(like);
                }
            }

            // 清理：DB 有但 Redis 没有的 → delete（用户已取消点赞）
            Set<Long> redisUserIdLongs = redisUserIds.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());
            for (Long dbUserId : dbUserIds) {
                if (!redisUserIdLongs.contains(dbUserId)) {
                    likeMapper.delete(
                            new LambdaQueryWrapper<AnnotationLike>()
                                    .eq(AnnotationLike::getAnnotationId, annotationId)
                                    .eq(AnnotationLike::getUserId, dbUserId)
                    );
                }
            }
        } catch (Exception e) {
            log.warn("同步 annotation_like 表失败: annotationId={}", annotationId, e);
        }
    }

    /**
     * 清除批注缓存（通过 annotationId 查找 chapterId）
     */
    private void evictAnnotationCache(Long annotationId) {
        try {
            Annotation annotation = annotationMapper.selectById(annotationId);
            if (annotation != null) {
                redisTemplate.delete(ANNOTATION_CACHE_PREFIX + annotation.getChapterId());
            }
        } catch (Exception e) {
            log.warn("清除批注缓存失败: annotationId={}", annotationId, e);
        }
    }
}
