package com.japy.module.ai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 检测器专用统计查询（只读，纯 SQL）。
 * 返回 Map 列表（key 为列别名小写），避免为统计建 DTO。
 */
@Mapper
public interface MonitorQueryMapper {

    /** 登录爆破：时间窗口内同一 IP 失败次数达阈值 */
    @Select("SELECT ipaddr AS ip, COUNT(*) AS cnt FROM sys_login_log " +
            "WHERE status = 1 AND login_time > NOW() - (#{windowMin} || ' minutes')::interval " +
            "GROUP BY ipaddr HAVING COUNT(*) >= #{maxFail}")
    List<Map<String, Object>> bruteForceIps(@Param("windowMin") int windowMin, @Param("maxFail") int maxFail);

    /** 时间范围内锁定次数（msg 含"锁定"） */
    @Select("SELECT COUNT(*) AS cnt FROM sys_login_log WHERE msg LIKE '%锁定%' AND login_time >= CAST(#{start} AS DATE)")
    long lockCountSince(@Param("start") String start);

    /** 异常时段写操作：最近 24h 内 23:00-06:00 的管理端写操作 */
    @Select("SELECT oper_name AS name, oper_url AS url, request_method AS method, cost_time AS cost, oper_time AS time " +
            "FROM sys_oper_log " +
            "WHERE request_method IN ('POST','PUT','DELETE') " +
            "AND oper_time > NOW() - interval '24 hours' " +
            "AND (EXTRACT(HOUR FROM oper_time) >= #{startHour} OR EXTRACT(HOUR FROM oper_time) < #{endHour}) " +
            "ORDER BY oper_time DESC LIMIT 20")
    List<Map<String, Object>> abnormalHourOps(@Param("startHour") int startHour, @Param("endHour") int endHour);

    /** 近 24h 各接口失败次数 */
    @Select("SELECT oper_url AS url, COUNT(*) AS cnt FROM sys_oper_log " +
            "WHERE status = 1 AND oper_time > NOW() - interval '24 hours' GROUP BY oper_url")
    List<Map<String, Object>> failCount24h();

    /** 近 7 天（不含 24h 窗口）各接口失败次数 */
    @Select("SELECT oper_url AS url, COUNT(*) AS cnt FROM sys_oper_log " +
            "WHERE status = 1 AND oper_time BETWEEN NOW() - interval '8 days' AND NOW() - interval '24 hours' " +
            "GROUP BY oper_url")
    List<Map<String, Object>> failCount7d();

    /** 慢操作：24h 内各接口超时次数/最大/平均耗时 */
    @Select("SELECT oper_url AS url, COUNT(*) AS cnt, MAX(cost_time) AS maxCost, ROUND(AVG(cost_time)) AS avgCost " +
            "FROM sys_oper_log WHERE cost_time > #{thresholdMs} AND oper_time > NOW() - interval '24 hours' " +
            "GROUP BY oper_url ORDER BY maxCost DESC LIMIT 10")
    List<Map<String, Object>> slowOps(@Param("thresholdMs") long thresholdMs);

    /** 近 24h 各接口总次数与失败次数（失败率检测） */
    @Select("SELECT oper_url AS url, COUNT(*) AS total, " +
            "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS fails " +
            "FROM sys_oper_log WHERE oper_time > NOW() - interval '24 hours' " +
            "GROUP BY oper_url HAVING COUNT(*) >= #{minSample}")
    List<Map<String, Object>> failRate(@Param("minSample") long minSample);

    /** 操作日志总行数 */
    @Select("SELECT COUNT(*) AS cnt FROM sys_oper_log")
    long operLogTotal();

    /** 某日期当天操作日志行数 */
    @Select("SELECT COUNT(*) AS cnt FROM sys_oper_log WHERE oper_time >= CAST(#{day} AS DATE) " +
            "AND oper_time < CAST(#{day} AS DATE) + interval '1 day'")
    long operLogCountOfDay(@Param("day") String day);

    /** 某日期当天登录日志行数 */
    @Select("SELECT COUNT(*) AS cnt FROM sys_login_log WHERE login_time >= CAST(#{day} AS DATE) " +
            "AND login_time < CAST(#{day} AS DATE) + interval '1 day'")
    long loginLogCountOfDay(@Param("day") String day);
}
