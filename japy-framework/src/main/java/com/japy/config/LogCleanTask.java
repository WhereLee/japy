package com.japy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.module.system.entity.SysLoginLog;
import com.japy.module.system.entity.SysOperLog;
import com.japy.module.system.mapper.SysLoginLogMapper;
import com.japy.module.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务：日志表定期清理（操作日志/登录日志保留 90 天），防止无限增长。
 * 分布式安全：多实例部署时用 Redisson 分布式锁保证同一时刻仅一台实例执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanTask {

    private static final int KEEP_DAYS = 90;
    /** 分布式锁 key + 获取锁超时（秒） */
    private static final String LOCK_KEY = "japy:task:log-clean";
    private static final long LOCK_WAIT_SECONDS = 3;

    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final RedissonClient redissonClient;

    /** 每天凌晨 3:30 执行 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanLogs() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            // 非阻塞获取：抢不到说明其他实例正在执行，本实例直接跳过
            if (!lock.tryLock(LOCK_WAIT_SECONDS, 0, TimeUnit.SECONDS)) {
                log.debug("日志清理任务被其他实例执行，本实例跳过");
                return;
            }
            LocalDateTime deadline = LocalDateTime.now().minusDays(KEEP_DAYS);
            int oper = operLogMapper.delete(new LambdaQueryWrapper<SysOperLog>()
                    .lt(SysOperLog::getOperTime, deadline));
            int login = loginLogMapper.delete(new LambdaQueryWrapper<SysLoginLog>()
                    .lt(SysLoginLog::getLoginTime, deadline));
            if (oper + login > 0) {
                log.info("日志清理完成：操作日志 {} 条，登录日志 {} 条（保留 {} 天）", oper, login, KEEP_DAYS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("日志清理任务被中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
