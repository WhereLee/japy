package com.recloud.controller;

import com.recloud.common.annotation.RateLimiter;
import com.recloud.common.result.R;
import com.recloud.dto.response.AuthResponse;
import com.recloud.dto.request.LoginRequest;
import com.recloud.dto.request.RefreshTokenRequest;
import com.recloud.dto.request.RegisterRequest;
import com.recloud.security.JwtTokenProvider;
import com.recloud.security.SecurityUtils;
import com.recloud.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口（公开，无需登录）
 */
@Tag(name = "认证管理", description = "注册/登录/刷新Token/登出")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    @RateLimiter(limit = 3, time = 60, key = "register")
    public R<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(
                request.getUsername(), request.getNickname(), request.getPassword()
        );
        return R.ok(response);
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    @RateLimiter(limit = 5, time = 300, key = "login")
    public R<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request.getUsername(), request.getPassword());
        return R.ok(response);
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public R<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        return R.ok(response);
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        String accessToken = resolveToken(request);
        String refreshToken = request.getHeader("X-Refresh-Token");
        Long userId = null;
        try {
            userId = SecurityUtils.getCurrentUserId();
        } catch (Exception ignored) {
            // 未登录也允许登出（清理 Token）
        }
        authService.logout(accessToken, refreshToken, userId);
        return R.ok();
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
