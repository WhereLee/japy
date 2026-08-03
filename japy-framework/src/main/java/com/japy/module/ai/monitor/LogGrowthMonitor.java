package com.japy.module.ai.monitor;

import com.japy.module.ai.mapper.MonitorQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 日志表增长检测：总量超警戒或按当前增速外推逼近警戒（警告 2） */
@Component
@RequiredArgsConstructor
public class LogGrowthMonitor implements Monitor {

    private final MonitorQueryMapper query;
    private final MonitorConfig cfg;

    @Override
    public String code() {
        return "log_table_growth";
    }

    @Override
    public String name() {
        return "日志表增长检测";
    }

    @Override
    public List<MonitorEvent> check() {
        long warnRows = cfg.getInt("monitor.logGrowth.warnRows", 5_000_000);
        long total = query.operLogTotal();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String today = LocalDate.now().format(fmt);
        String yesterday = LocalDate.now().minusDays(1).format(fmt);
        long todayRows = query.operLogCountOfDay(today);
        long yesterdayRows = query.operLogCountOfDay(yesterday);
        long daily = Math.max(todayRows, yesterdayRows);

        List<MonitorEvent> events = new ArrayList<>();
        if (total >= warnRows) {
            events.add(MonitorEvent.builder()
                    .monitorCode(code())
                    .monitorName(name())
                    .severity(2)
                    .fingerprint(code() + ":warn")
                    .summary(String.format("操作日志表已达 %d 行，超过警戒值 %d 行，查询性能将受影响，建议尽快归档",
                            total, warnRows))
                    .evidence(Map.of("totalRows", total, "warnRows", warnRows))
                    .build());
        } else if (daily > 0 && total + daily * 30 >= warnRows) {
            long days = (warnRows - total) / daily;
            events.add(MonitorEvent.builder()
                    .monitorCode(code())
                    .monitorName(name())
                    .severity(2)
                    .fingerprint(code() + ":growth")
                    .summary(String.format("操作日志表当前 %d 行，按日增 %d 行计算约 %d 天后达到警戒值 %d 行，建议提前规划归档",
                            total, daily, days, warnRows))
                    .evidence(Map.of("totalRows", total, "dailyRows", daily, "daysToWarn", days, "warnRows", warnRows))
                    .build());
        }
        return events;
    }
}
