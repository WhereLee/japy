package com.japy.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 操作日志 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private Integer businessType;    // 0其他 1新增 2修改 3删除
    private String method;
    private String requestMethod;
    private String operName;
    private String operUrl;
    private String operIp;
    private String operParam;
    private String jsonResult;
    private Integer status;          // 0成功 1失败
    private String errorMsg;
    private Long costTime;
    private LocalDateTime operTime;
}
