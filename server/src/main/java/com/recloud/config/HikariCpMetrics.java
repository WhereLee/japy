package com.recloud.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * HikariCP 连接池 Micrometer 监控
 * <p>
 * 将 HikariCP 的关键指标注册到 Micrometer，
 * 可通过 /actuator/metrics 查看：
 * <ul>
 *   <li>hikaricp.connections.active — 活跃连接数</li>
 *   <li>hikaricp.connections.idle — 空闲连接数</li>
 *   <li>hikaricp.connections.total — 总连接数</li>
 *   <li>hikaricp.connections.pending — 等待获取连接的线程数</li>
 *   <li>hikaricp.connections.max — 最大连接数配置</li>
 *   <li>hikaricp.connections.min — 最小空闲连接配置</li>
 *   <li>hikaricp.connections.timeout — 连接超时次数</li>
 * </ul>
 * <p>
 * 面试可讲：
 * - 连接池耗尽是常见线上事故原因，必须有监控
 * - pending > 0 说明连接不够用，需要扩容或优化慢 SQL
 * - active/total 比值持续 > 80% 需要告警
 */
@Slf4j
@Component
@ConditionalOnBean({DataSource.class, MeterRegistry.class})
public class HikariCpMetrics {

    public HikariCpMetrics(DataSource dataSource, MeterRegistry registry) {
        if (dataSource instanceof HikariDataSource hikari) {
            String poolName = hikari.getPoolName();

            Gauge.builder("hikaricp.connections.active", hikari, ds -> ds.getHikariPoolMXBean() != null ? ds.getHikariPoolMXBean().getActiveConnections() : 0)
                    .description("活跃连接数")
                    .tag("pool", poolName)
                    .register(registry);

            Gauge.builder("hikaricp.connections.idle", hikari, ds -> ds.getHikariPoolMXBean() != null ? ds.getHikariPoolMXBean().getIdleConnections() : 0)
                    .description("空闲连接数")
                    .tag("pool", poolName)
                    .register(registry);

            Gauge.builder("hikaricp.connections.total", hikari, ds -> ds.getHikariPoolMXBean() != null ? ds.getHikariPoolMXBean().getTotalConnections() : 0)
                    .description("总连接数")
                    .tag("pool", poolName)
                    .register(registry);

            Gauge.builder("hikaricp.connections.pending", hikari, ds -> ds.getHikariPoolMXBean() != null ? ds.getHikariPoolMXBean().getThreadsAwaitingConnection() : 0)
                    .description("等待获取连接的线程数")
                    .tag("pool", poolName)
                    .register(registry);

            Gauge.builder("hikaricp.connections.max", hikari, HikariDataSource::getMaximumPoolSize)
                    .description("最大连接数配置")
                    .tag("pool", poolName)
                    .register(registry);

            Gauge.builder("hikaricp.connections.min", hikari, HikariDataSource::getMinimumIdle)
                    .description("最小空闲连接配置")
                    .tag("pool", poolName)
                    .register(registry);

            log.info("HikariCP 监控指标已注册到 Micrometer: pool={}", poolName);
        } else {
            log.warn("DataSource 非 HikariCP 类型，跳过连接池监控注册");
        }
    }
}
