package com.japy.module.ai.monitor;

import com.japy.module.ai.mapper.MonitorQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 接口失败率检测：24h 失败率超阈值且样本足够（警告 2） */
@Component
@RequiredArgsConstructor
public class ApiFailRateMonitor implements Monitor {

    private final MonitorQueryMapper query;
    private final MonitorConfig cfg;

    @Override
    public String code() {
        return "api_fail_rate";
    }

    @Override
    public String name() {
        return "接口失败率检测";
    }

    @Override
    public List<MonitorEvent> check() {
        double ratio = cfg.getDouble("monitor.apiFailRate.ratio", 0.03);
        long minSample = cfg.getInt("monitor.apiFailRate.minSample", 50);
        List<MonitorEvent> events = new ArrayList<>();
        for (Map<String, Object> r : query.failRate(minSample)) {
            String url = String.valueOf(r.get("url"));
            long total = ((Number) r.get("total")).longValue();
            long fails = ((Number) r.get("fails")).longValue();
            double rate = (double) fails / total;
            if (rate > ratio) {
                events.add(MonitorEvent.builder()
                        .monitorCode(code())
                        .monitorName(name())
                        .severity(2)
                        .fingerprint(code() + ":" + url)
                        .summary(String.format("接口 %s 近 24h 失败率 %.1f%%（%d/%d），超过阈值 %.1f%%",
                                url, rate * 100, fails, total, ratio * 100))
                        .evidence(Map.of("url", url, "failRate", rate, "fails", fails, "total", total))
                        .build());
            }
        }
        return events;
    }
}
