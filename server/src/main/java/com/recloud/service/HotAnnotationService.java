package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recloud.entity.Annotation;
import com.recloud.mapper.AnnotationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 热门批注排行服务
 * <p>
 * 设计思路：
 * - 使用 Redis ZSET 存储热门批注排行，score = 综合评分
 * - 定时任务每 30 分钟重算一次排行
 * - 评分公式：score = likes × timeDecay + comments × 2
 *   - timeDecay = 1 / (1 + hoursSinceCreation / 24)
 *   - 新批注衰减慢（分高），老批注衰减快（分低）
 *   - 评论权重是点赞的 2 倍（评论代表更深度的互动）
 * <p>
 * 对比原方案（每次查询时 ORDER BY like_count）：
 * - 原方案：每次请求都 ORDER BY + LIMIT，数据量大时慢
 * - 新方案：预计算排行，查询 O(logN + M)，M = 返回条数
 * <p>
 * 降级方案：Redis 不可用时直接查 DB（ORDER BY like_count DESC LIMIT）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotAnnotationService {

    private final AnnotationMapper annotationMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private static final String HOT_ANNOTATION_KEY = "hot:annotations";
    /** 排行榜最多保留 200 条 */
    private static final int MAX_RANK_SIZE = 200;
    /** 默认返回条数 */
    private static final int DEFAULT_TOP_N = 20;

    /**
     * 获取热门批注 ID 列表（按评分降序）
     *
     * @param topN 返回条数，默认 20
     * @return 批注 ID 列表（按评分降序）
     */
    public List<Long> getHotAnnotationIds(int topN) {
        if (topN <= 0) topN = DEFAULT_TOP_N;

        try {
            // ZREVRANGE：按 score 降序取前 topN 个
            Set<String> ids = redisTemplate.opsForZSet()
                    .reverseRange(HOT_ANNOTATION_KEY, 0, topN - 1);

            if (ids != null && !ids.isEmpty()) {
                return ids.stream().map(Long::parseLong).toList();
            }

            // ZSET 为空，触发一次刷新并返回 DB 查询
            refreshHotRanking();
            return getHotAnnotationIdsFallback(topN);

        } catch (Exception e) {
            log.warn("Redis 查询热门排行失败，降级到DB: {}", e.getMessage());
            return getHotAnnotationIdsFallback(topN);
        }
    }

    /**
     * 获取热门批注完整数据（含评分）
     */
    public List<Map<String, Object>> getHotAnnotations(int topN) {
        List<Long> ids = getHotAnnotationIds(topN);
        if (ids.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : ids) {
            Annotation annotation = annotationMapper.selectById(id);
            if (annotation != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", annotation.getId());
                item.put("chapterId", annotation.getChapterId());
                item.put("userId", annotation.getUserId());
                item.put("selectedText", annotation.getSelectedText());
                item.put("content", annotation.getContent());
                item.put("likeCount", annotation.getLikeCount());
                item.put("commentCount", annotation.getCommentCount());
                item.put("createdAt", annotation.getCreatedAt());

                // 从 ZSET 获取评分
                try {
                    Double score = redisTemplate.opsForZSet()
                            .score(HOT_ANNOTATION_KEY, String.valueOf(id));
                    item.put("hotScore", score != null ? Math.round(score * 100.0) / 100.0 : 0);
                } catch (Exception e) {
                    item.put("hotScore", 0);
                }
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 定时任务：每 30 分钟刷新热门排行
     * <p>
     * 评分公式：score = likes × timeDecay + comments × 2
     * timeDecay = 1 / (1 + hoursSinceCreation / 24)
     * <p>
     * 执行策略：
     * - 查询最近 7 天的批注（太老的批注不参与排行）
     * - 计算评分后写入 Redis ZSET
     * - 保留 TOP 200，其余淘汰
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void refreshHotRanking() {
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(7);
            List<Annotation> annotations = annotationMapper.selectList(
                    new LambdaQueryWrapper<Annotation>()
                            .ge(Annotation::getCreatedAt, since)
            );

            if (annotations.isEmpty()) {
                log.debug("热门排行刷新：最近7天无批注");
                return;
            }

            // 批量写入 ZSET（使用 Pipeline 或逐个 ZADD）
            LocalDateTime now = LocalDateTime.now();
            for (Annotation annotation : annotations) {
                double score = calculateHotScore(annotation, now);
                if (score > 0) {
                    redisTemplate.opsForZSet().add(HOT_ANNOTATION_KEY,
                            String.valueOf(annotation.getId()), score);
                }
            }

            // 只保留 TOP MAX_RANK_SIZE
            Long totalSize = redisTemplate.opsForZSet().zCard(HOT_ANNOTATION_KEY);
            if (totalSize != null && totalSize > MAX_RANK_SIZE) {
                // 删除排名最后的（totalSize - MAX_RANK_SIZE）个
                redisTemplate.opsForZSet().removeRange(HOT_ANNOTATION_KEY,
                        0, totalSize - MAX_RANK_SIZE - 1);
            }

            // 设置过期时间（防止冷数据永久驻留）
            redisTemplate.expire(HOT_ANNOTATION_KEY, Duration.ofDays(1));

            log.info("热门排行刷新完成：共 {} 条批注参与计算", annotations.size());

        } catch (Exception e) {
            log.error("热门排行刷新失败: {}", e.getMessage());
        }
    }

    /**
     * 计算批注的热门评分
     * <p>
     * 公式：score = likes × timeDecay + comments × 2
     * timeDecay = 1 / (1 + hoursSinceCreation / 24)
     * <p>
     * 示例：
     * - 刚创建的批注（0h）：likes × 1.0 + comments × 2
     * - 1天前的批注：likes × 0.5 + comments × 2
     * - 3天前的批注：likes × 0.25 + comments × 2
     * - 7天前的批注：likes × 0.125 + comments × 2
     */
    private double calculateHotScore(Annotation annotation, LocalDateTime now) {
        int likes = annotation.getLikeCount() != null ? annotation.getLikeCount() : 0;
        int comments = annotation.getCommentCount() != null ? annotation.getCommentCount() : 0;

        // 没有任何互动的批注不参与排行
        if (likes == 0 && comments == 0) return 0;

        // 时间衰减因子
        long hoursSinceCreation = Duration.between(annotation.getCreatedAt(), now).toHours();
        double timeDecay = 1.0 / (1.0 + hoursSinceCreation / 24.0);

        return likes * timeDecay + comments * 2.0;
    }

    /**
     * DB 降级查询（ORDER BY like_count DESC）
     */
    private List<Long> getHotAnnotationIdsFallback(int topN) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Annotation> annotations = annotationMapper.selectList(
                new LambdaQueryWrapper<Annotation>()
                        .ge(Annotation::getCreatedAt, since)
                        .gt(Annotation::getLikeCount, 0)
                        .orderByDesc(Annotation::getLikeCount)
                        .last("LIMIT " + topN)
        );
        return annotations.stream().map(Annotation::getId).toList();
    }
}
