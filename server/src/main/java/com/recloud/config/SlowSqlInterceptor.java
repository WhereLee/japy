package com.recloud.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MyBatis 慢 SQL 拦截器
 *
 * 功能：
 * 1. 记录超过阈值的 SQL 到日志（WARN 级别）
 * 2. 用 Micrometer Timer 记录所有 SQL 执行耗时（可被 /actuator/metrics 查询）
 *
 * 日志示例：
 * [SLOW SQL] cost=1234ms | SELECT * FROM annotation WHERE chapter_id = ?
 *
 * Micrometer 指标：
 * /actuator/metrics/mybatis.sql.execution — 查看 SQL 统计（按类型分组）
 */
@Slf4j
@Component
@Intercepts({
    @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, org.apache.ibatis.session.ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
})
public class SlowSqlInterceptor implements Interceptor {

    /**
     * 慢 SQL 阈值（毫秒）
     */
    private static final long SLOW_SQL_THRESHOLD_MS = 500;

    private static MeterRegistry meterRegistry;

    /** Timer 实例缓存：避免每条 SQL 都创建新的 Timer builder 链 */
    private static final ConcurrentHashMap<String, Timer> TIMER_CACHE = new ConcurrentHashMap<>();

    @Autowired
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        SlowSqlInterceptor.meterRegistry = meterRegistry;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = handler.getBoundSql();
        String sql = boundSql.getSql().replaceAll("[\\s]+", " ").trim();

        String sqlType = extractSqlType(sql);

        long start = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            // Micrometer Timer 记录所有 SQL（使用缓存的 Timer 实例）
            if (meterRegistry != null) {
                Timer timer = TIMER_CACHE.computeIfAbsent(sqlType, type ->
                        Timer.builder("mybatis.sql.execution")
                                .tag("type", type)
                                .description("SQL 执行耗时")
                                .register(meterRegistry)
                );
                timer.record(costMs, TimeUnit.MILLISECONDS);
            }

            // 慢 SQL 告警
            if (costMs >= SLOW_SQL_THRESHOLD_MS) {
                log.warn("[SLOW SQL] cost={}ms | {}", costMs, sql);
            } else {
                log.debug("[SQL] cost={}ms | {}", costMs, sql);
            }
        }
    }

    private String extractSqlType(String sql) {
        String upper = sql.toUpperCase().trim();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        return "OTHER";
    }
}
