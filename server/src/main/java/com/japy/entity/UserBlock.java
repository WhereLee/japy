package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_block")
public class UserBlock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long blockedUserId;
    private LocalDateTime createdAt;
}
