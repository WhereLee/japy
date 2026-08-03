package com.japy.module.ai.monitor;

import com.japy.module.ai.mapper.MonitorQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 慢操作检测：24h 内耗时超阈值的接口（警告 2） */
@Component
@RequiredArgsConstructor
public class SlowOpsMonitor implements Monitor {

    private final MonitorQueryMapper query;
    private final MonitorConfig cfg;

    @Override
    public String code() {
        return "slow_ops";
    }

    @Override
    public String name() {
        return "慢操作检测";
    }

    @Override
    public List<MonitorEvent> check() {
        long threshold = cfg.getInt("monitor.slowOps.thresholdMs", 5000);
        List<Map<String, Object>> rows = query.slowOps(threshold);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<MonitorEvent> events = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String url = String.valueOf(r.get("url"));
            long cnt = ((Number) r.get("cnt")).longValue();
            long maxCost = ((Number) r.get("maxcost")).longValue();
            events.add(MonitorEvent.builder()
                    .monitorCode(code())
                    .monitorName(name())
                    .severity(2)
                    .fingerprint(code() + ":" + url)
                    .summary(String.format("接口 %s 近 24h 有 %d 次操作超过 %dms（最大 %dms），存在性能隐患",
                            url, cnt, threshold, maxCost))
                    .evidence(Map.of("url", url, "overCount", cnt, "maxCostMs", maxCost, "thresholdMs", threshold))
                    .build());
        }
        return events;
    }
}
