package com.recloud.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 线程池监控器
 * <p>
 * 每 30 秒打印线程池状态，队列使用率 > 80% 告警。
 * 面试可讲：生产环境通过 Metrics 暴露给 Prometheus + Grafana。
 */
@Slf4j
@Configuration
public class ThreadPoolMonitor {

    private final Map<String, Executor> executors;

    public ThreadPoolMonitor(Map<String, Executor> executors) {
        this.executors = executors;
    }

    /**
     * 每 30 秒打印线程池状态
     */
    @Scheduled(fixedRate = 30000)
    public void monitor() {
        executors.forEach((name, executor) -> {
            if (executor instanceof ThreadPoolTaskExecutor taskExecutor) {
                java.util.concurrent.ThreadPoolExecutor pool = taskExecutor.getThreadPoolExecutor();

                int activeCount = pool.getActiveCount();
                int poolSize = pool.getPoolSize();
                int corePoolSize = pool.getCorePoolSize();
                int maxPoolSize = pool.getMaximumPoolSize();
                int queueSize = pool.getQueue().size();
                int queueCapacity = pool.getQueue().remainingCapacity() + queueSize;
                double queueUsage = queueCapacity > 0 ? (double) queueSize / queueCapacity * 100 : 0;

                log.info("[线程池监控] {} - active={}/{}, pool={}/{}, queue={}/{} ({}%)",
                        name, activeCount, corePoolSize, poolSize, maxPoolSize,
                        queueSize, queueCapacity, String.format("%.1f", queueUsage));

                // 队列使用率 > 80% 告警
                if (queueUsage > 80) {
                    log.warn("[线程池告警] {} 队列使用率 {}% > 80%，考虑扩容或优化任务", name, String.format("%.1f", queueUsage));
                }
            }
        });
    }
}
