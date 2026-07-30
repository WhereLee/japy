package com.recloud.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置
 * <p>
 * 设计依据：
 * - logExecutor：日志写入专用，IO 密集型，核心2/最大5/队列100
 *   - 日志写入频率不高，核心线程数无需太多
 *   - 队列 100 足够缓冲突发（如批量操作触发大量日志）
 *   - CallerRunsPolicy：队列满时由调用线程执行，保证日志不丢失
 * - 优雅停机：实现 DisposableBean，shutdown 时等待任务完成
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class ThreadPoolConfig {

    /**
     * 操作日志专用线程池
     * <p>
     * 参数设计：
     * - corePoolSize=2：日志写入频率低，2 个线程足够
     * - maxPoolSize=5：突发流量时扩容，但不过多占用资源
     * - queueCapacity=100：缓冲突发日志写入
     * - keepAliveSeconds=60：空闲线程 60s 后回收
     * - rejectedExecutionHandler=CallerRunsPolicy：队列满时由调用线程执行，不丢日志
     */
    @Bean("logExecutor")
    public Executor logExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true); // 优雅停机
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("日志线程池初始化完成: core={}, max={}, queue={}", 2, 5, 100);
        return executor;
    }

    /**
     * 分布式锁看门狗调度器（单线程，守护锁续期）
     * <p>
     * 参数设计：
     * - poolSize=1：看门狗只需单线程轮询，每个锁的续期任务极轻量
     * - waitForTasksToCompleteOnShutdown=true：停机时等待续期任务完成
     * - awaitTerminationSeconds=5：最多等待 5s
     */
    @Bean("lockWatchdogScheduler")
    public TaskScheduler lockWatchdogScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("lock-watchdog-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.initialize();
        log.info("看门狗调度器初始化完成");
        return scheduler;
    }

    /**
     * Dashboard 专用查询线程池
     * <p>
     * 参数设计：
     * - corePoolSize=4：Dashboard 有 7~8 个并行查询，4 个线程足够（DB 是瓶颈）
     * - maxPoolSize=8：突发时可扩容
     * - queueCapacity=50：Dashboard 查询不会积压太多
     * - CallerRunsPolicy：队列满时由调用线程执行，保证查询不丢
     * <p>
     * 为什么不用 ForkJoinPool.commonPool()？
     * - commonPool 是全局共享的，Dashboard 的慢查询会阻塞其他任务
     * - 自定义线程池实现资源隔离
     */
    @Bean("dashboardExecutor")
    public Executor dashboardExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("dashboard-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        log.info("Dashboard线程池初始化完成: core={}, max={}, queue={}", 4, 8, 50);
        return executor;
    }
}
