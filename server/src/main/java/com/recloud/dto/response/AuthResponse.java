package com.recloud.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录/注册响应（包含双 Token + 用户基本信息）
 * <p>
 * 属于响应 DTO，置于 dto/response 包，与入参 DTO（dto/request）分离。
 */
@Data
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String username;
    private String nickname;
    private String role;
}
