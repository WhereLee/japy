package com.japy.module.ai.monitor;

import com.japy.module.ai.mapper.MonitorQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 异常时段操作检测：23:00-06:00 的管理端写操作（警告 2） */
@Component
@RequiredArgsConstructor
public class AbnormalHourMonitor implements Monitor {

    private final MonitorQueryMapper query;
    private final MonitorConfig cfg;

    @Override
    public String code() {
        return "abnormal_hour_ops";
    }

    @Override
    public String name() {
        return "异常时段操作检测";
    }

    @Override
    public List<MonitorEvent> check() {
        int startHour = cfg.getInt("monitor.abnormalHour.start", 23);
        int endHour = cfg.getInt("monitor.abnormalHour.end", 6);
        List<Map<String, Object>> rows = query.abnormalHourOps(startHour, endHour);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<MonitorEvent> events = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(rows.size(), 5); i++) {
            Map<String, Object> r = rows.get(i);
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(r.get("name")).append(" ").append(r.get("method")).append(" ").append(r.get("url"));
        }
        events.add(MonitorEvent.builder()
                .monitorCode(code())
                .monitorName(name())
                .severity(2)
                .fingerprint(code() + ":" + java.time.LocalDate.now())
                .summary(String.format("最近 24 小时内 %d:%02d-%02d:%02d 时段有 %d 条管理端写操作（如：%s），请确认是否为预期操作",
                        startHour, 0, endHour, 0, rows.size(), sb))
                .evidence(Map.of("count", rows.size(), "samples", rows.subList(0, Math.min(rows.size(), 5))))
                .build());
        return events;
    }
}
