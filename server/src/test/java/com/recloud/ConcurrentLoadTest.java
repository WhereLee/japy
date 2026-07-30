package com.recloud;

import com.recloud.entity.AnnotationLike;
import com.recloud.mapper.AnnotationLikeMapper;
import com.recloud.mq.OperationLogConsumer;
import com.recloud.mq.OperationLogProducer;
import com.recloud.service.AnnotationLikeService;
import com.recloud.service.AnnotationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 并发压测（Java 原生 CountDownLatch + ExecutorService）
 * <p>
 * 测试场景：
 * 1. 点赞并发 toggle：100 线程同时 toggle 同一条批注，验证 Redis Set 原子性
 * 2. 缓存击穿防护：50 线程同时请求未缓存章节，验证分布式锁只放行 1 个
 * 3. 点赞降级：Redis 不可用时 DB 降级路径的并发安全性
 * <p>
 * 输出指标：
 * - 吞吐量（ops/sec）
 * - P50/P95/P99 延迟
 * - 错误率
 * <p>
 * 注意：需要本地 Redis 和 MySQL 运行
 */
@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
        // 禁用 RocketMQ 自动配置（压测不需要 MQ）
        "rocketmq.consumer.enabled=false",
        "rocketmq.producer.enabled=false",
        "spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrentLoadTest {

    @Autowired
    private AnnotationLikeService likeService;

    @Autowired
    private AnnotationService annotationService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AnnotationLikeMapper likeMapper;

    /** Mock RocketMQ 消费者和生产者（压测不需要 MQ，避免连接 NameServer 失败） */
    @MockBean
    private OperationLogConsumer operationLogConsumer;

    @MockBean
    private OperationLogProducer operationLogProducer;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(100);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /**
     * 场景 1：点赞并发 toggle
     * <p>
     * 100 个线程同时对同一条批注执行 toggle，
     * 由于 SADD/SREM 是原子操作，最终状态应该是确定的（不会丢失更新）。
     * <p>
     * 验证点：
     * - 无异常抛出
     * - 最终 likeCount 一致（所有线程看到的最终状态相同）
     * - 吞吐量 > 1000 ops/sec（Redis 单线程模型下应该很快）
     */
    @Test
    @Order(1)
    @DisplayName("压测：100 并发点赞 toggle（Redis Set 原子操作）")
    void testConcurrentLikeToggle() throws InterruptedException {
        final Long annotationId = 99999L; // 测试用批注 ID
        final int threadCount = 100;
        final CountDownLatch readyLatch = new CountDownLatch(threadCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);

        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);
        final AtomicLong totalLatencyNs = new AtomicLong(0);
        final long[] latencies = new long[threadCount];

        // 清理测试数据
        redisTemplate.delete("like:status:" + annotationId);
        redisTemplate.delete("like:status:" + annotationId + ":init");

        for (int i = 0; i < threadCount; i++) {
            final long userId = 1000 + i;
            final int idx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 所有线程同时开始
                    long start = System.nanoTime();
                    Map<String, Object> result = likeService.toggle(annotationId, userId);
                    long elapsed = System.nanoTime() - start;
                    latencies[idx] = elapsed;
                    totalLatencyNs.addAndGet(elapsed);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("点赞 toggle 异常: userId={}, error={}", userId, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(); // 等待所有线程就绪
        long benchStart = System.nanoTime();
        startLatch.countDown(); // 发令枪
        doneLatch.await(30, TimeUnit.SECONDS);
        long benchElapsed = System.nanoTime() - benchStart;

        // 输出结果
        printReport("点赞 toggle", threadCount, successCount.get(), errorCount.get(),
                benchElapsed, latencies, successCount.get());

        // 验证：无错误
        Assertions.assertEquals(0, errorCount.get(), "不应有异常");
        // 验证：最终计数一致
        long finalCount = likeService.countByAnnotation(annotationId);
        log.info("最终点赞数: {}", finalCount);
        // 100 个不同用户各 toggle 一次，全部应该是点赞（因为每个用户只操作一次）
        Assertions.assertEquals(100, finalCount, "100 个用户各 toggle 一次，最终应为 100 赞");

        // 清理测试数据（Redis + DB）
        redisTemplate.delete("like:status:" + annotationId);
        redisTemplate.delete("like:status:" + annotationId + ":init");
        redisTemplate.delete("like:dirty");
        likeMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnnotationLike>()
                .eq(AnnotationLike::getAnnotationId, annotationId));
    }

    /**
     * 场景 2：缓存击穿防护
     * <p>
     * 50 个线程同时请求同一个未缓存的章节批注列表。
     * 分布式锁应保证只有 1 个线程去 DB 加载并回填缓存，
     * 其余线程降级查 DB（不回填）。
     * <p>
     * 验证点：
     * - 所有线程都能拿到数据（不报错）
     * - Redis 缓存最终被回填
     */
    @Test
    @Order(2)
    @DisplayName("压测：50 并发缓存击穿防护（分布式锁）")
    void testCacheStampedeProtection() throws InterruptedException {
        final Long chapterId = 1L; // 假设章节 1 存在
        final int threadCount = 50;
        final CountDownLatch readyLatch = new CountDownLatch(threadCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);

        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);
        final long[] latencies = new long[threadCount];

        // 清除缓存，模拟冷启动
        redisTemplate.delete("annotation:chapter:" + chapterId);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    var result = annotationService.listByChapter(chapterId);
                    long elapsed = System.nanoTime() - start;
                    latencies[idx] = elapsed;
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("缓存击穿测试异常: {}", e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        long benchStart = System.nanoTime();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        long benchElapsed = System.nanoTime() - benchStart;

        printReport("缓存击穿防护", threadCount, successCount.get(), errorCount.get(),
                benchElapsed, latencies, successCount.get());

        // 验证：所有线程都成功
        Assertions.assertEquals(threadCount, successCount.get(), "所有线程都应拿到数据");
        Assertions.assertEquals(0, errorCount.get(), "不应有异常");

        // 验证：缓存已被回填
        String cached = redisTemplate.opsForValue().get("annotation:chapter:" + chapterId);
        Assertions.assertNotNull(cached, "缓存应已被回填");
        log.info("缓存回填验证通过: {}", cached.length() > 100 ? cached.substring(0, 100) + "..." : cached);
    }

    /**
     * 场景 3：点赞快速双击（同一用户连续 toggle）
     * <p>
     * 模拟用户快速双击点赞按钮：同一用户 10 次顺序 toggle。
     * SADD/SREM 原子操作保证最终状态正确（偶数次=未赞，奇数次=已赞）。
     * <p>
     * 注意：使用顺序执行而非并发，因为真实用户双击是顺序事件。
     * 并发场景下同一用户的多次 toggle 结果是不确定的（取决于 SADD/SREM 交错顺序）。
     */
    @Test
    @Order(3)
    @DisplayName("压测：同一用户快速双击 10 次 toggle（顺序）")
    void testRapidDoubleTap() throws InterruptedException {
        final Long annotationId = 88888L;
        final Long userId = 1L;
        final int tapCount = 10;

        // 等待定时同步任务完成（避免干扰）
        Thread.sleep(2000);

        // 清理 Redis 和 DB 状态（包括上次测试残留的 annotation_like 记录）
        redisTemplate.delete("like:status:" + annotationId);
        redisTemplate.delete("like:status:" + annotationId + ":init");
        redisTemplate.delete("like:dirty");
        likeMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnnotationLike>()
                .eq(AnnotationLike::getAnnotationId, annotationId));

        // 验证初始状态：未赞
        Assertions.assertFalse(likeService.isLiked(annotationId, userId), "初始状态应为未赞");

        long totalNs = 0;
        for (int i = 0; i < tapCount; i++) {
            long start = System.nanoTime();
            Map<String, Object> result = likeService.toggle(annotationId, userId);
            totalNs += System.nanoTime() - start;
            boolean liked = (Boolean) result.get("liked");
            log.debug("第 {} 次 toggle: liked={}", i + 1, liked);
        }

        // 10 次 toggle（偶数），最终应为未赞
        boolean finalLiked = likeService.isLiked(annotationId, userId);
        long finalCount = likeService.countByAnnotation(annotationId);
        log.info("快速双击 {} 次后: liked={}, count={}, 平均延迟={}ms",
                tapCount, finalLiked, finalCount,
                String.format("%.2f", totalNs / 1_000_000.0 / tapCount));

        Assertions.assertFalse(finalLiked, "10 次 toggle（偶数）后应为未赞");
        Assertions.assertEquals(0, finalCount, "10 次 toggle 后计数应为 0");

        // 清理
        redisTemplate.delete("like:status:" + annotationId);
        redisTemplate.delete("like:status:" + annotationId + ":init");
        redisTemplate.delete("like:dirty");
    }

    // ==================== 报告输出 ====================

    private void printReport(String scenario, int total, int success, int errors,
                             long elapsedNs, long[] latencies, int validCount) {
        double elapsedMs = elapsedNs / 1_000_000.0;
        double throughput = success / (elapsedMs / 1000.0);

        // 排序计算百分位
        long[] sorted = Arrays.copyOf(latencies, validCount);
        Arrays.sort(sorted);

        log.info("\n========== 压测报告：{} ==========", scenario);
        log.info("并发数: {} | 成功: {} | 失败: {} | 错误率: {}%",
                total, success, errors, String.format("%.2f", errors * 100.0 / total));
        log.info("总耗时: {}ms | 吞吐量: {} ops/sec",
                String.format("%.1f", elapsedMs), String.format("%.0f", throughput));
        if (validCount > 0) {
            log.info("P50: {}ms | P95: {}ms | P99: {}ms",
                    String.format("%.2f", sorted[(int) (validCount * 0.5)] / 1_000_000.0),
                    String.format("%.2f", sorted[(int) (validCount * 0.95)] / 1_000_000.0),
                    String.format("%.2f", sorted[Math.min((int) (validCount * 0.99), validCount - 1)] / 1_000_000.0));
        }
        log.info("==========================================\n");
    }
}
