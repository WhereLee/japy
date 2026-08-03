package com.japy.module.ai.monitor;

import com.japy.module.ai.mapper.MonitorQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 审计缺口检测：当天系统有登录行为但操作日志为 0（审计链路可能中断，严重 3） */
@Component
@RequiredArgsConstructor
public class AuditGapMonitor implements Monitor {

    private final MonitorQueryMapper query;

    @Override
    public String code() {
        return "audit_gap";
    }

    @Override
    public String name() {
        return "审计缺口检测";
    }

    @Override
    public List<MonitorEvent> check() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        long oper = query.operLogCountOfDay(today);
        long login = query.loginLogCountOfDay(today);
        // 当天有人登录但操作日志为空 → 审计链路可能断了
        if (login > 0 && oper == 0) {
            List<MonitorEvent> events = new ArrayList<>();
            events.add(MonitorEvent.builder()
                    .monitorCode(code())
                    .monitorName(name())
                    .severity(3)
                    .fingerprint(code() + ":" + today)
                    .summary(String.format("今天已有 %d 次登录行为，但操作日志为 0 条，审计链路可能中断（MQ/切面异常）", login))
                    .evidence(Map.of("todayLoginCount", login, "todayOperCount", oper))
                    .build());
            return events;
        }
        return List.of();
    }
}
