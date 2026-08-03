package com.japy.module.ai.monitor;

import com.japy.module.ai.mapper.MonitorQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 登录爆破检测：时间窗口内同一 IP 失败次数达阈值（严重 3） */
@Component
@RequiredArgsConstructor
public class LoginBruteForceMonitor implements Monitor {

    private final MonitorQueryMapper query;
    private final MonitorConfig cfg;

    @Override
    public String code() {
        return "login_brute_force";
    }

    @Override
    public String name() {
        return "登录爆破检测";
    }

    @Override
    public List<MonitorEvent> check() {
        int windowMin = cfg.getInt("monitor.loginBruteForce.windowMin", 5);
        int maxFail = cfg.getInt("monitor.loginBruteForce.maxFail", 5);
        List<MonitorEvent> events = new ArrayList<>();
        for (Map<String, Object> row : query.bruteForceIps(windowMin, maxFail)) {
            String ip = String.valueOf(row.get("ip"));
            long cnt = ((Number) row.get("cnt")).longValue();
            events.add(MonitorEvent.builder()
                    .monitorCode(code())
                    .monitorName(name())
                    .severity(3)
                    .fingerprint(code() + ":" + ip)
                    .summary(String.format("IP %s 在最近 %d 分钟内登录失败 %d 次（阈值 %d 次），疑似口令爆破", ip, windowMin, cnt, maxFail))
                    .evidence(Map.of("ip", ip, "failCount", cnt, "windowMin", windowMin))
                    .build());
        }
        return events;
    }
}
