package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("points_log")
public class PointsLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String action;
    private Integer points;
    private LocalDateTime createdAt;
}
