package com.japy.module.ai.monitor;

import com.japy.module.ai.mapper.MonitorQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 锁定风暴检测：当日锁定次数超过近 7 日均值 N 倍（严重 3） */
@Component
@RequiredArgsConstructor
public class LockStormMonitor implements Monitor {

    private final MonitorQueryMapper query;
    private final MonitorConfig cfg;

    @Override
    public String code() {
        return "lock_storm";
    }

    @Override
    public String name() {
        return "锁定风暴检测";
    }

    @Override
    public List<MonitorEvent> check() {
        double ratio = cfg.getDouble("monitor.lockStorm.ratio", 3);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String today = LocalDate.now().format(fmt);
        String weekAgo = LocalDate.now().minusDays(7).format(fmt);

        long todayLock = query.lockCountSince(today);
        // 近 7 天锁定总量（不含今日）→ 日均
        long weekLock = query.lockCountSince(weekAgo) - todayLock;
        double dailyAvg = weekLock / 7.0;

        if (todayLock >= 5 && dailyAvg > 0 && todayLock > dailyAvg * ratio) {
            List<MonitorEvent> events = new ArrayList<>();
            events.add(MonitorEvent.builder()
                    .monitorCode(code())
                    .monitorName(name())
                    .severity(3)
                    .fingerprint(code() + ":" + today)
                    .summary(String.format("今日账号锁定 %d 次，为近 7 日均值（%.1f 次/天）的 %.1f 倍，可能正在被集中爆破",
                            todayLock, dailyAvg, todayLock / dailyAvg))
                    .evidence(Map.of("todayLockCount", todayLock, "dailyAvg", dailyAvg, "ratio", ratio))
                    .build());
            return events;
        }
        return List.of();
    }
}
