package com.japy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.japy.entity.PointsLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PointsLogMapper extends BaseMapper<PointsLog> {

    @Select("SELECT COALESCE(SUM(points),0) FROM points_log WHERE user_id=#{userId} AND action IN ('post','comment') AND DATE(created_at)=CURDATE()")
    int todayContributionPoints(Long userId);

    @Select("SELECT COALESCE(SUM(points),0) FROM points_log WHERE user_id=#{userId} AND action IN ('liked','comment_liked','featured') AND DATE(created_at)=CURDATE()")
    int todayRecognitionPoints(Long userId);
}
