package com.japy.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.japy.module.ai.entity.AiMonitorEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AiMonitorEventMapper extends BaseMapper<AiMonitorEvent> {

    /**
     * 去重：同指纹在最近 windowMinutes 内是否已存在任何事件。
     * 注意：不区分状态（含已确认/已忽略）——否则管理员确认/忽略后 30 分钟内会被重复报警。
     */
    @Select("SELECT COUNT(*) FROM ai_monitor_event " +
            "WHERE fingerprint = #{fp} AND created_at > NOW() - (#{windowMin} || ' minutes')::interval")
    long countRecentByFingerprint(@Param("fp") String fp, @Param("windowMin") long windowMin);
}
