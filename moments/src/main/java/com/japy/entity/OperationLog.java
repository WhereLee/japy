package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adminId;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private Integer costMs;         // 操作耗时（毫秒）
    private String method;          // 请求方法与路径
    private String ip;
    private String error;           // 失败时的错误信息
    private LocalDateTime createdAt;
}
