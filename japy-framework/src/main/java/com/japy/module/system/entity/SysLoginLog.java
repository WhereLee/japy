package com.japy.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 登录日志 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String ipaddr;
    private Integer status;         // 0成功 1失败
    private String msg;
    private LocalDateTime loginTime;
}
