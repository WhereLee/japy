package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recloud.common.entity.OperationLog;
import com.recloud.common.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 操作日志服务
 * <p>
 * 管理端审计日志查询：
 * - 支持模块筛选、操作人筛选、状态筛选
 * - 支持时间范围查询
 * - 支持关键词搜索（请求参数/URL）
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    /**
     * 分页查询操作日志（多维度筛选）
     *
     * @param module     模块名筛选（批注/用户/小说等）
     * @param operator   操作人昵称筛选
     * @param status     状态筛选（SUCCESS/FAIL）
     * @param keyword    关键词搜索（请求URL/参数）
     * @param startTime  开始时间
     * @param endTime    结束时间
     */
    public IPage<OperationLog> listLogs(int page, int size, String module,
                                        String operator, String status,
                                        String keyword,
                                        String startTime, String endTime) {
        Page<OperationLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        if (module != null && !module.isEmpty()) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (operator != null && !operator.isEmpty()) {
            wrapper.eq(OperationLog::getOperatorName, operator);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(OperationLog::getStatus, status.toUpperCase());
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(OperationLog::getRequestUrl, keyword)
                    .or().like(OperationLog::getRequestParams, keyword)
                    .or().like(OperationLog::getOperation, keyword));
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(OperationLog::getCreatedAt, LocalDateTime.parse(startTime));
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(OperationLog::getCreatedAt, LocalDateTime.parse(endTime));
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);

        return operationLogMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 查询所有操作人列表（用于前端下拉筛选）
     */
    public java.util.List<String> listOperators() {
        return operationLogMapper.selectList(
                new LambdaQueryWrapper<OperationLog>()
                        .select(OperationLog::getOperatorName)
                        .isNotNull(OperationLog::getOperatorName)
                        .ne(OperationLog::getOperatorName, "")
                        .groupBy(OperationLog::getOperatorName)
        ).stream().map(OperationLog::getOperatorName).distinct().toList();
    }
}
