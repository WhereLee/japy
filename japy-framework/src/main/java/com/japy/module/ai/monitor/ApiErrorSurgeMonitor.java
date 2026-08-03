package com.japy.module.ai.monitor;

import com.japy.module.ai.mapper.MonitorQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 接口错误突增检测：24h 失败计数 > 7 日均值 × N（严重 3） */
@Component
@RequiredArgsConstructor
public class ApiErrorSurgeMonitor implements Monitor {

    private final MonitorQueryMapper query;
    private final MonitorConfig cfg;

    @Override
    public String code() {
        return "api_error_surge";
    }

    @Override
    public String name() {
        return "接口错误突增检测";
    }

    @Override
    public List<MonitorEvent> check() {
        double ratio = cfg.getDouble("monitor.apiErrorSurge.ratio", 3);
        long minSample = cfg.getInt("monitor.apiErrorSurge.minSample", 20);

        Map<String, Long> c24 = toMap(query.failCount24h());
        Map<String, Long> c7 = toMap(query.failCount7d());
        if (c24.isEmpty()) {
            return List.of();
        }
        List<MonitorEvent> events = new ArrayList<>();
        for (Map.Entry<String, Long> e : c24.entrySet()) {
            long today = e.getValue();
            long week = c7.getOrDefault(e.getKey(), 0L);
            double dailyAvg = week / 7.0;
            if (today >= minSample && dailyAvg > 0 && today > dailyAvg * ratio) {
                events.add(MonitorEvent.builder()
                        .monitorCode(code())
                        .monitorName(name())
                        .severity(3)
                        .fingerprint(code() + ":" + e.getKey())
                        .summary(String.format("接口 %s 近 24h 失败 %d 次，为近 7 日均值（%.1f 次/天）的 %.1f 倍",
                                e.getKey(), today, dailyAvg, today / dailyAvg))
                        .evidence(Map.of("url", e.getKey(), "fail24h", today, "dailyAvg", dailyAvg, "ratio", ratio))
                        .build());
            }
        }
        return events;
    }

    private Map<String, Long> toMap(List<Map<String, Object>> rows) {
        Map<String, Long> m = new HashMap<>();
        for (Map<String, Object> r : rows) {
            m.put(String.valueOf(r.get("url")), ((Number) r.get("cnt")).longValue());
        }
        return m;
    }
}
