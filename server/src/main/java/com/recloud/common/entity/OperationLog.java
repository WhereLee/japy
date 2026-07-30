package com.recloud.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 * <p>
 * 由 LogAspect 异步写入，记录所有关键操作的请求/响应/耗时/操作人信息。
 */
@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String module;
    private String operation;
    private String method;
    private String requestMethod;
    private String requestUrl;
    private String requestParams;
    private String responseResult;
    private String status;
    private String errorMessage;
    private Long executeTime;
    private Long operatorId;
    private String operatorName;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
