package com.japy.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.japy.module.ai.entity.AiFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiFeedbackMapper extends BaseMapper<AiFeedback> {

    /** 按检测器统计反馈指标（好评数/误报数） */
    @Select("SELECT e.monitor_code AS monitorCode, e.monitor_name AS monitorName, " +
            "COUNT(f.id) AS total, " +
            "SUM(CASE WHEN f.rating = 1 THEN 1 ELSE 0 END) AS good, " +
            "SUM(CASE WHEN f.reason_tag = '误报' THEN 1 ELSE 0 END) AS falsePositive " +
            "FROM ai_feedback f JOIN ai_monitor_event e ON f.target_type = 'event' AND f.target_id = e.id " +
            "GROUP BY e.monitor_code, e.monitor_name ORDER BY total DESC")
    List<Map<String, Object>> statsByMonitor();
}
