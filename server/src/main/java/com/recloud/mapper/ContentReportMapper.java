package com.recloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.recloud.entity.ContentReport;
import com.recloud.vo.AdminReportVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContentReportMapper extends BaseMapper<ContentReport> {

    /**
     * 管理端举报分页查询（XML 实现）
     * <p>
     * 两次 LEFT JOIN user 关联举报人/处理人昵称，直接产出 AdminReportVO，消除 N+1。
     * 分页由 MyBatis-Plus 分页拦截器自动接管（第一个参数为 IPage）。
     */
    IPage<AdminReportVO> selectAdminReportPage(
            IPage<AdminReportVO> page,
            @Param("status") String status);
}
