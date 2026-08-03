package com.japy.module.auth.controller;

import com.japy.common.R;
import com.japy.module.auth.dto.LoginDTO;
import com.japy.module.auth.dto.RegisterDTO;
import com.japy.module.auth.service.AuthService;
import com.japy.module.auth.vo.TokenVO;
import com.japy.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口：注册 / 登录 / 刷新 / 登出
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public R<TokenVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        return R.ok(authService.register(dto, request));
    }

    @PostMapping("/login")
    public R<TokenVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return R.ok(authService.login(dto, request));
    }

    /** 刷新 access token（body: {refreshToken}） */
    @PostMapping("/refresh")
    public R<TokenVO> refresh(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return R.ok(authService.refresh(body.get("refreshToken"), request));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            authService.logout(loginUser.getUserId());
        }
        return R.ok();
    }
}
