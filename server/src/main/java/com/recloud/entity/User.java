package com.recloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("user")
public class User extends BaseEntity {
    private String username;
    private String nickname;
    @TableField(select = false)
    private String password;
    private String role;
    private Integer status;
    private Integer loginFailCount;
    private LocalDateTime lockTime;
    private LocalDateTime lastLoginAt;
}
