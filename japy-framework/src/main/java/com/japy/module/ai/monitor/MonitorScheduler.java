package com.japy.module.ai.monitor;

import com.japy.module.ai.service.AiMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * AI 监测调度器：定时执行全部检测器。
 * 分布式安全：多实例部署时仅一台实例执行（Redisson 锁，与 LogCleanTask 同模式）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorScheduler {

    private static final String LOCK_KEY = "japy:task:ai-monitor";

    private final RedissonClient redissonClient;
    private final AiMonitorService monitorService;

    @Scheduled(fixedDelayString = "${ai.monitor.scheduler-interval-ms:1800000}")
    public void run() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            if (!lock.tryLock(3, 0, TimeUnit.SECONDS)) {
                log.debug("AI 监测任务被其他实例执行，本实例跳过");
                return;
            }
            int created = monitorService.checkAll();
            if (created > 0) {
                log.info("AI 监测完成，新增信号 {} 条", created);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI 监测任务被中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
