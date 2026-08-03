package com.japy.module.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 个人信息返回（不含密码等敏感字段） */
@Data
public class UserInfoVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer sex;
    private LocalDateTime createTime;
}
