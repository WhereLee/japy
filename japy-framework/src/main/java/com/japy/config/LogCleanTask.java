package com.japy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.module.system.entity.SysLoginLog;
import com.japy.module.system.entity.SysOperLog;
import com.japy.module.system.mapper.SysLoginLogMapper;
import com.japy.module.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时任务：日志表定期清理（操作日志/登录日志保留 90 天），防止无限增长。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanTask {

    private static final int KEEP_DAYS = 90;

    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;

    /** 每天凌晨 3:30 执行 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanLogs() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(KEEP_DAYS);
        int oper = operLogMapper.delete(new LambdaQueryWrapper<SysOperLog>()
                .lt(SysOperLog::getOperTime, deadline));
        int login = loginLogMapper.delete(new LambdaQueryWrapper<SysLoginLog>()
                .lt(SysLoginLog::getLoginTime, deadline));
        if (oper + login > 0) {
            log.info("日志清理完成：操作日志 {} 条，登录日志 {} 条（保留 {} 天）", oper, login, KEEP_DAYS);
        }
    }
}
