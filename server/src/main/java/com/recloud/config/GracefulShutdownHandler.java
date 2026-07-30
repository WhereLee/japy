package com.recloud.config;

import com.recloud.common.lock.RedisDistributedLock;
import com.recloud.service.AnnotationLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/**
 * 优雅停机处理器
 * <p>
 * 服务停止时的执行顺序：
 * 1. 停止接收新请求（由 Spring Boot graceful shutdown 控制）
 * 2. 点赞数据刷盘：立即执行一次 syncLikesToDb()，防止 Redis 中的点赞数据丢失
 * 3. 释放分布式锁：让其他节点可以接管
 * 4. 线程池排空：等待已有任务完成（由各线程池的 waitForTasksToComplete 控制）
 * <p>
 * 为什么需要优雅停机？
 * - 点赞数据存在 Redis，30s 定时同步到 DB
 * - 如果服务在这 30s 内停止，Redis 中最新的点赞数据就丢了
 * - 优雅停机时立即触发一次同步，确保数据不丢失
 * - 分布式锁如果不释放，其他节点要等锁过期才能获取，影响可用性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GracefulShutdownHandler implements ApplicationListener<ContextClosedEvent> {

    private final AnnotationLikeService annotationLikeService;
    private final RedisDistributedLock distributedLock;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("========== 优雅停机开始 ==========");

        // 1. 点赞数据刷盘（Redis → DB）
        try {
            log.info("步骤1: 点赞数据刷盘到DB...");
            annotationLikeService.syncLikesToDb();
            log.info("点赞数据刷盘完成");
        } catch (Exception e) {
            log.error("点赞数据刷盘失败: {}", e.getMessage());
        }

        // 2. 释放当前节点持有的所有分布式锁
        try {
            log.info("步骤2: 释放分布式锁...");
            distributedLock.releaseAllLocks();
            log.info("分布式锁释放完成");
        } catch (Exception e) {
            log.error("分布式锁释放失败: {}", e.getMessage());
        }

        log.info("========== 优雅停机完成 ==========");
    }
}
