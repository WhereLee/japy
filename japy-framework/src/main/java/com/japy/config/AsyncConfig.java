package com.japy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * 背景：@EnableAsync 无自定义 Executor 时，Spring 用 SimpleAsyncTaskExecutor——
 * 每次任务新建线程、无上限（并发上传触发 RAG 同步/操作日志等会线程无界）。
 * 本配置：
 *  - core=2 / max=4：RAG 同步等重任务串行化为主，少量并发即可
 *  - queue=100：突发任务排队，不丢
 *  - CallerRuns：队列满时由提交线程执行（慢速背压，不静默丢弃）
 *  - 线程名前缀 jasy-async- 便于日志区分
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("japy-async-");
        // 队列满：由提交线程执行（背压），而非 AbortPolicy 抛异常
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
