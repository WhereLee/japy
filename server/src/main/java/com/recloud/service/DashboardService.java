package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recloud.entity.Annotation;
import com.recloud.entity.ContentReport;
import com.recloud.entity.Novel;
import com.recloud.entity.User;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.mapper.CommentMapper;
import com.recloud.mapper.ContentReportMapper;
import com.recloud.mapper.NovelMapper;
import com.recloud.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 仪表盘服务 —— 聚合统计数据
 * <p>
 * 高并发优化：使用 CompletableFuture 并行查询多个统计指标
 * <p>
 * 对比串行方案：
 * - 串行：7 个 SQL 依次执行，总耗时 = SQL1 + SQL2 + ... + SQL7
 * - 并行：7 个 SQL 同时执行，总耗时 = max(SQL1, SQL2, ..., SQL7)
 * - 对于 Dashboard 这种多指标聚合查询，并行可以显著降低响应时间
 * <p>
 * 线程池隔离：使用专用 dashboardExecutor，而非 ForkJoinPool.commonPool()
 * - commonPool 是全局共享的，Dashboard 的慢查询会阻塞其他使用 commonPool 的任务
 * - 自定义线程池实现了资源隔离，Dashboard 的并发不会影响其他业务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserMapper userMapper;
    private final AnnotationMapper annotationMapper;
    private final CommentMapper commentMapper;
    private final NovelMapper novelMapper;
    private final ContentReportMapper reportMapper;

    @Qualifier("dashboardExecutor")
    private final Executor dashboardExecutor;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 查询超时时间（秒） */
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    /** 日报 Redis key 前缀 */
    private static final String DAILY_REPORT_KEY = "dashboard:daily:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 获取仪表盘全部统计数据（CompletableFuture 并行查询）
     * <p>
     * 优化点：
     * 1. 7 个统计查询并行执行，总耗时 = 最慢的一个查询
     * 2. 使用专用线程池 dashboardExecutor，资源隔离
     * 3. 设置超时保护（5s），防止单个慢查询拖垮整个 Dashboard
     * 4. 超时或异常的指标用默认值 0，不影响其他指标展示
     */
    public Map<String, Object> getDashboardData() {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();

        // 并行提交所有查询任务
        CompletableFuture<Long> userCount = queryAsync(
                () -> userMapper.selectCount(null), "userCount");
        CompletableFuture<Long> annotationCount = queryAsync(
                () -> annotationMapper.selectCount(null), "annotationCount");
        CompletableFuture<Long> commentCount = queryAsync(
                () -> commentMapper.selectCount(null), "commentCount");
        CompletableFuture<Long> novelCount = queryAsync(
                () -> novelMapper.selectCount(null), "novelCount");
        CompletableFuture<Long> todayNewUsers = queryAsync(
                () -> userMapper.selectCount(
                        new LambdaQueryWrapper<User>().ge(User::getCreatedAt, todayStart)),
                "todayNewUsers");
        CompletableFuture<Long> todayNewAnnotations = queryAsync(
                () -> annotationMapper.selectCount(
                        new LambdaQueryWrapper<Annotation>().ge(Annotation::getCreatedAt, todayStart)),
                "todayNewAnnotations");
        CompletableFuture<Long> pendingReportCount = queryAsync(
                () -> reportMapper.selectCount(
                        new LambdaQueryWrapper<ContentReport>().eq(ContentReport::getStatus, "pending")),
                "pendingReportCount");
        CompletableFuture<Long> disabledUserCount = queryAsync(
                () -> userMapper.selectCount(
                        new LambdaQueryWrapper<User>().eq(User::getStatus, 0)),
                "disabledUserCount");

        // 等待所有查询完成（带超时保护）
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                userCount, annotationCount, commentCount, novelCount,
                todayNewUsers, todayNewAnnotations, pendingReportCount, disabledUserCount
        );

        try {
            allFutures.get(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Dashboard 查询超时（{}s），部分指标可能使用默认值", QUERY_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("Dashboard 查询异常: {}", e.getMessage());
        }

        // 组装结果（超时或异常的指标用 0 兜底）
        Map<String, Object> data = new HashMap<>();
        data.put("userCount", getOrDefault(userCount));
        data.put("annotationCount", getOrDefault(annotationCount));
        data.put("commentCount", getOrDefault(commentCount));
        data.put("novelCount", getOrDefault(novelCount));
        data.put("todayNewUsers", getOrDefault(todayNewUsers));
        data.put("todayNewAnnotations", getOrDefault(todayNewAnnotations));
        data.put("pendingReportCount", getOrDefault(pendingReportCount));
        data.put("disabledUserCount", getOrDefault(disabledUserCount));

        return data;
    }

    /**
     * 异步执行查询（使用专用线程池）
     * <p>
     * 异常处理：单个查询失败不影响其他查询，返回 0
     */
    private CompletableFuture<Long> queryAsync(QueryTask task, String metricName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.execute();
            } catch (Exception e) {
                log.warn("Dashboard 查询失败: metric={}, error={}", metricName, e.getMessage());
                return 0L;
            }
        }, dashboardExecutor);
    }

    /**
     * 安全获取 CompletableFuture 结果（超时或异常返回 0）
     */
    private long getOrDefault(CompletableFuture<Long> future) {
        try {
            return future.getNow(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 查询任务函数式接口
     */
    @FunctionalInterface
    private interface QueryTask {
        long execute();
    }

    // ==================== 日报生成 ====================

    /**
     * 定时任务：每天凌晨 00:05 生成昨日日报
     * <p>
     * 日报内容：
     * - 昨日新增用户数、批注数、评论数
     * - 昨日处理举报数
     * - 与前日对比的增长率
     * <p>
     * 存储：Redis String（JSON），保留 30 天
     * 用途：管理员查看趋势分析，而非只看当前快照数据
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void generateDailyReport() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate dayBeforeYesterday = yesterday.minusDays(1);

        log.info("开始生成日报: date={}", yesterday);

        try {
            LocalDateTime yesterdayStart = yesterday.atStartOfDay();
            LocalDateTime yesterdayEnd = yesterday.plusDays(1).atStartOfDay();
            LocalDateTime prevDayStart = dayBeforeYesterday.atStartOfDay();
            LocalDateTime prevDayEnd = yesterday.atStartOfDay();

            // 并行查询昨日数据
            CompletableFuture<Long> newUsers = queryAsync(
                    () -> userMapper.selectCount(
                            new LambdaQueryWrapper<User>()
                                    .ge(User::getCreatedAt, yesterdayStart)
                                    .lt(User::getCreatedAt, yesterdayEnd)),
                    "dailyNewUsers");
            CompletableFuture<Long> newAnnotations = queryAsync(
                    () -> annotationMapper.selectCount(
                            new LambdaQueryWrapper<Annotation>()
                                    .ge(Annotation::getCreatedAt, yesterdayStart)
                                    .lt(Annotation::getCreatedAt, yesterdayEnd)),
                    "dailyNewAnnotations");
            CompletableFuture<Long> newComments = queryAsync(
                    () -> commentMapper.selectCount(
                            new LambdaQueryWrapper<com.recloud.entity.Comment>()
                                    .ge(com.recloud.entity.Comment::getCreatedAt, yesterdayStart)
                                    .lt(com.recloud.entity.Comment::getCreatedAt, yesterdayEnd)),
                    "dailyNewComments");
            CompletableFuture<Long> resolvedReports = queryAsync(
                    () -> reportMapper.selectCount(
                            new LambdaQueryWrapper<ContentReport>()
                                    .ge(ContentReport::getUpdatedAt, yesterdayStart)
                                    .lt(ContentReport::getUpdatedAt, yesterdayEnd)
                                    .ne(ContentReport::getStatus, "pending")),
                    "dailyResolvedReports");

            // 并行查询前日数据（用于计算增长率）
            CompletableFuture<Long> prevNewUsers = queryAsync(
                    () -> userMapper.selectCount(
                            new LambdaQueryWrapper<User>()
                                    .ge(User::getCreatedAt, prevDayStart)
                                    .lt(User::getCreatedAt, prevDayEnd)),
                    "prevNewUsers");
            CompletableFuture<Long> prevNewAnnotations = queryAsync(
                    () -> annotationMapper.selectCount(
                            new LambdaQueryWrapper<Annotation>()
                                    .ge(Annotation::getCreatedAt, prevDayStart)
                                    .lt(Annotation::getCreatedAt, prevDayEnd)),
                    "prevNewAnnotations");

            CompletableFuture.allOf(newUsers, newAnnotations, newComments,
                    resolvedReports, prevNewUsers, prevNewAnnotations)
                    .get(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // 组装日报
            Map<String, Object> report = new HashMap<>();
            report.put("date", yesterday.toString());
            report.put("newUsers", getOrDefault(newUsers));
            report.put("newAnnotations", getOrDefault(newAnnotations));
            report.put("newComments", getOrDefault(newComments));
            report.put("resolvedReports", getOrDefault(resolvedReports));

            // 计算增长率
            report.put("userGrowthRate", calcGrowthRate(
                    getOrDefault(newUsers), getOrDefault(prevNewUsers)));
            report.put("annotationGrowthRate", calcGrowthRate(
                    getOrDefault(newAnnotations), getOrDefault(prevNewAnnotations)));

            // 存入 Redis（保留 30 天）
            String key = DAILY_REPORT_KEY + yesterday.format(DATE_FMT);
            String json = objectMapper.writeValueAsString(report);
            redisTemplate.opsForValue().set(key, json,
                    java.time.Duration.ofDays(30));

            log.info("日报生成完成: date={}, newUsers={}, newAnnotations={}",
                    yesterday, getOrDefault(newUsers), getOrDefault(newAnnotations));

        } catch (TimeoutException e) {
            log.warn("日报生成超时: date={}", yesterday);
        } catch (Exception e) {
            log.error("日报生成失败: date={}, error={}", yesterday, e.getMessage());
        }
    }

    /**
     * 获取指定日期的日报数据
     */
    public Map<String, Object> getDailyReport(String date) {
        try {
            String key = DAILY_REPORT_KEY + date;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> report = objectMapper.readValue(json,
                        new TypeReference<Map<String, Object>>() {});
                return report;
            }
        } catch (Exception e) {
            log.warn("读取日报失败: date={}, error={}", date, e.getMessage());
        }
        return Map.of("message", "该日期无日报数据");
    }

    /**
     * 获取最近 N 天的日报列表
     */
    public java.util.List<Map<String, Object>> getRecentDailyReports(int days) {
        java.util.List<Map<String, Object>> reports = new java.util.ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= days; i++) {
            Map<String, Object> report = getDailyReport(today.minusDays(i).format(DATE_FMT));
            if (report.containsKey("date")) {
                reports.add(report);
            }
        }
        return reports;
    }

    /**
     * 计算增长率（百分比）
     * 公式：(current - previous) / previous × 100
     * previous = 0 时，current > 0 返回 100%，否则返回 0
     */
    private double calcGrowthRate(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round((current - previous) * 1000.0 / previous) / 10.0;
    }
}
