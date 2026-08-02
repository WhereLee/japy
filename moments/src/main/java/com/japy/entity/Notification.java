package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;            // like / comment / reply / report_result / announce
    private String refType;         // moment / comment
    private Long refId;
    private String content;
    private Integer isRead;
    private LocalDateTime createdAt;
}
