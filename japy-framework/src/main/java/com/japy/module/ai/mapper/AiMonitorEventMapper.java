package com.japy.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.japy.module.ai.entity.AiMonitorEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AiMonitorEventMapper extends BaseMapper<AiMonitorEvent> {

    /** 去重：同指纹在最近 windowMinutes 内是否已有待处理/已处理信号 */
    @Select("SELECT COUNT(*) FROM ai_monitor_event " +
            "WHERE fingerprint = #{fp} AND created_at > NOW() - (#{windowMin} || ' minutes')::interval " +
            "AND status IN (0, 1)")
    long countRecentByFingerprint(@Param("fp") String fp, @Param("windowMin") long windowMin);
}
