package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("report")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reporterId;
    private String targetType;      // moment / comment
    private Long targetId;
    private String reason;
    private Integer status;         // 0待处理 1已处理 2已驳回
    private String result;
    private LocalDateTime createdAt;
}
