package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recloud.common.exception.BizException;
import com.recloud.common.lock.RedisDistributedLock;
import com.recloud.common.lock.RedisLock;
import com.recloud.common.result.ResultCode;
import com.recloud.config.BusinessMetrics;
import com.recloud.dto.request.CreateAnnotationRequest;
import com.recloud.entity.Annotation;
import com.recloud.entity.Chapter;
import com.recloud.entity.Comment;
import com.recloud.entity.AnnotationLike;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.mapper.ChapterMapper;
import com.recloud.mapper.CommentMapper;
import com.recloud.mapper.AnnotationLikeMapper;
import com.recloud.strategy.AnnotationTypeHandler;
import com.recloud.strategy.AnnotationTypeHandlerFactory;
import com.recloud.vo.AdminAnnotationVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;

/**
 * 批注服务
 * <p>
 * 缓存策略（Cache-Aside 模式）：
 * - 读：先查 Redis → 未命中查 DB → 回填 Redis（TTL 5min）
 * - 写：写 DB → 删除 Redis 缓存（不是更新，避免并发问题）
 * - 防穿透：空值缓存 TTL 60s
 * - 防击穿：分布式锁保证同一 chapterId 只有一个线程去 DB 加载
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationService {

    private final AnnotationMapper annotationMapper;
    private final ChapterMapper chapterMapper;
    private final CommentMapper commentMapper;
    private final AnnotationLikeMapper likeMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AnnotationTypeHandlerFactory handlerFactory;
    private final BusinessMetrics businessMetrics;
    private final RedisDistributedLock distributedLock;

    private static final String CACHE_PREFIX = "annotation:chapter:";
    private static final String LOCK_PREFIX = "annotation:lock:";
    private static final long CACHE_TTL_MINUTES = 5;
    private static final long EMPTY_TTL_SECONDS = 60;
    private static final long LOCK_TTL_SECONDS = 5;

    /**
     * 创建批注（策略模式 + 写操作 → 删缓存）
     */
    @Transactional(rollbackFor = Exception.class)
    public Annotation create(Long chapterId, Long userId, Integer anchorStart,
                             Integer anchorEnd, String selectedText, String content, Integer type) {
        int annotationType = type != null ? type : 0;

        // 校验章节是否存在
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BizException(ResultCode.CHAPTER_NOT_FOUND);
        }

        // 策略模式：根据类型获取处理器，执行校验
        AnnotationTypeHandler handler = handlerFactory.getHandler(annotationType);
        CreateAnnotationRequest request = new CreateAnnotationRequest();
        request.setChapterId(chapterId);
        request.setAnchorStart(anchorStart);
        request.setAnchorEnd(anchorEnd);
        request.setSelectedText(selectedText);
        request.setContent(content);
        request.setType(annotationType);
        handler.validate(request);

        // 重复内容检测：同一用户对同一章节30秒内不能发相同批注
        Long duplicateCount = annotationMapper.selectCount(
                new LambdaQueryWrapper<Annotation>()
                        .eq(Annotation::getChapterId, chapterId)
                        .eq(Annotation::getUserId, userId)
                        .eq(Annotation::getContent, content)
                        .ge(Annotation::getCreatedAt, java.time.LocalDateTime.now().minusSeconds(30))
        );
        if (duplicateCount > 0) {
            throw new BizException(ResultCode.ANNOTATION_DUPLICATE);
        }

        Annotation annotation = new Annotation();
        annotation.setChapterId(chapterId);
        annotation.setUserId(userId);
        annotation.setAnchorStart(anchorStart);
        annotation.setAnchorEnd(anchorEnd);
        annotation.setSelectedText(selectedText);
        annotation.setContent(content);
        annotation.setType(annotationType);
        annotationMapper.insert(annotation);

        // 策略模式：创建后处理
        handler.afterCreate(annotation);

        // Cache-Aside: 写后删缓存
        evictCache(chapterId);
        businessMetrics.incrementAnnotationCreated();
        return annotation;
    }

    /**
     * 按用户查询批注列表（分页）
     */
    public List<Annotation> listByUser(Long userId, int page, int size) {
        return annotationMapper.selectList(
                new LambdaQueryWrapper<Annotation>()
                        .eq(Annotation::getUserId, userId)
                        .orderByDesc(Annotation::getCreatedAt)
                        .last("LIMIT " + size + " OFFSET " + (page - 1) * size)
        );
    }

    /**
     * 按章节查询批注列表（Redis L2 缓存 + 分页）
     */
    public List<Annotation> listByChapter(Long chapterId, int page, int size) {
        List<Annotation> all = listByChapter(chapterId);
        int from = (page - 1) * size;
        if (from >= all.size()) return Collections.emptyList();
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    /**
     * 按章节查询批注列表（Redis L2 缓存 + 防击穿分布式锁）
     * <p>
     * 流程：Redis → 分布式锁（UUID + Lua + 看门狗）→ DB → 回填 Redis
     * 防穿透：空结果缓存 60s
     * 防击穿：缓存 miss 时加分布式锁，只有一个线程去 DB 并回填，其余降级查 DB 不回填
     * <p>
     * 对比旧方案的改进：
     * - 旧方案：setIfAbsent("1") + delete()，非原子释放，可能误删别人的锁
     * - 新方案：RedisDistributedLock（UUID + Lua 原子释放 + 看门狗续期）
     * - 旧方案：未获锁线程也 fillRedis()，多线程同时回填
     * - 新方案：未获锁线程只查 DB 不回填，避免并发写入
     */
    public List<Annotation> listByChapter(Long chapterId) {
        String cacheKey = CACHE_PREFIX + chapterId;
        String lockKey = LOCK_PREFIX + chapterId;

        // 1. 查 Redis
        List<Annotation> cached = getFromRedis(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存未命中，尝试获取分布式锁（UUID + Lua + 看门狗）
        RedisLock lock = distributedLock.tryLock(lockKey, LOCK_TTL_SECONDS);

        if (lock != null) {
            // 获得锁的线程：查 DB 并回填缓存
            try {
                // 双重检查：可能其他线程已经回填了缓存
                cached = getFromRedis(cacheKey);
                if (cached != null) return cached;

                List<Annotation> list = loadFromDB(chapterId);
                fillRedis(cacheKey, list);
                return list;
            } finally {
                distributedLock.unlock(lock);
            }
        } else {
            // 未获得锁：说明另一个线程正在回填缓存
            // 只查 DB 不回填（避免多线程并发写入 Redis）
            // 锁持有者会回填 Redis，下次请求自然命中缓存
            log.debug("未获得缓存回填锁，降级查DB: chapterId={}", chapterId);
            return loadFromDB(chapterId);
        }
    }

    /**
     * 从 Redis 获取缓存（含空值防穿透处理）
     */
    private List<Annotation> getFromRedis(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                if ("EMPTY".equals(json)) {
                    return Collections.emptyList();
                }
                return objectMapper.readValue(json, new TypeReference<List<Annotation>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis 查询批注缓存失败，降级查DB: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从 DB 加载批注列表
     */
    private List<Annotation> loadFromDB(Long chapterId) {
        return annotationMapper.selectList(
                new LambdaQueryWrapper<Annotation>()
                        .eq(Annotation::getChapterId, chapterId)
                        .orderByAsc(Annotation::getAnchorStart)
        );
    }

    /**
     * 回填 Redis 缓存（含空值防穿透）
     */
    private void fillRedis(String cacheKey, List<Annotation> list) {
        try {
            if (list.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, "EMPTY", EMPTY_TTL_SECONDS, TimeUnit.SECONDS);
            } else {
                String json = objectMapper.writeValueAsString(list);
                redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("Redis 回填批注缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 删除批注（级联删除评论和点赞 + 删缓存）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAnnotation(Long annotationId, Long userId) {
        Annotation annotation = annotationMapper.selectById(annotationId);
        if (annotation == null) return false;

        int rows = annotationMapper.delete(
                new LambdaQueryWrapper<Annotation>()
                        .eq(Annotation::getId, annotationId)
                        .eq(Annotation::getUserId, userId)
        );
        if (rows > 0) {
            cascadeDeleteAnnotationData(annotationId);
            evictCache(annotation.getChapterId());
            businessMetrics.incrementAnnotationDeleted();
        }
        return rows > 0;
    }

    /**
     * 管理员删除批注（级联删除评论和点赞 + 删缓存）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean adminDeleteAnnotation(Long annotationId) {
        Annotation annotation = annotationMapper.selectById(annotationId);
        if (annotation == null) return false;

        int rows = annotationMapper.deleteById(annotationId);
        if (rows > 0) {
            cascadeDeleteAnnotationData(annotationId);
            evictCache(annotation.getChapterId());
            businessMetrics.incrementAnnotationDeleted();
        }
        return rows > 0;
    }

    /**
     * 级联删除批注关联数据（评论 + 点赞）
     */
    private void cascadeDeleteAnnotationData(Long annotationId) {
        commentMapper.delete(
                new LambdaQueryWrapper<Comment>().eq(Comment::getAnnotationId, annotationId));
        likeMapper.delete(
                new LambdaQueryWrapper<AnnotationLike>().eq(AnnotationLike::getAnnotationId, annotationId));
    }

    /**
     * 管理员分页查询批注（支持关键词搜索 + 类型筛选）
     */
    public IPage<Annotation> listAnnotations(int page, int size, String keyword, Integer type) {
        Page<Annotation> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Annotation> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Annotation::getContent, keyword)
                    .or().like(Annotation::getSelectedText, keyword);
        }
        if (type != null) {
            wrapper.eq(Annotation::getType, type);
        }
        wrapper.orderByDesc(Annotation::getCreatedAt);
        return annotationMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 管理端批注分页查询（含用户昵称/章节标题/小说标题）
     * <p>
     * 走 XML 联表查询，一次 SQL 完成多表关联，避免 N+1。
     */
    public IPage<AdminAnnotationVO> listAdminAnnotations(int page, int size, String keyword, Integer type) {
        Page<AdminAnnotationVO> pageParam = new Page<>(page, size);
        return annotationMapper.selectAdminAnnotationPage(pageParam, keyword, type);
    }

    /**
     * 删除批注缓存（Cache-Aside: 删缓存而非更新）
     */
    private void evictCache(Long chapterId) {
        try {
            redisTemplate.delete(CACHE_PREFIX + chapterId);
        } catch (Exception e) {
            log.warn("删除批注缓存失败: {}", e.getMessage());
        }
    }
}
