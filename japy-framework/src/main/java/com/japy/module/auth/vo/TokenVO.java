package com.japy.module.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 登录成功返回 */
@Data
@AllArgsConstructor
public class TokenVO {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;         // access 有效期（秒）
    private Long userId;
    private String nickname;
    private String avatar;
    private List<String> roles;
}
