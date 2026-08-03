package com.japy.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 公告 */
@Data
@TableName("sys_notice")
public class SysNotice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String noticeTitle;
    private Integer noticeType;      // 1通知 2公告
    private String noticeContent;
    private Integer status;          // 0正常 1关闭
    private Long createBy;
    private LocalDateTime createTime;
}
