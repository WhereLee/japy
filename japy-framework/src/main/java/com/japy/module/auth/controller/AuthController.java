package com.japy.module.auth.controller;

import com.japy.common.R;
import com.japy.aspect.RateLimit;
import com.japy.module.auth.dto.LoginDTO;
import com.japy.module.auth.dto.RegisterDTO;
import com.japy.module.auth.service.AuthService;
import com.japy.module.auth.service.MenuTreeService;
import com.japy.module.auth.vo.RouterVo;
import com.japy.module.auth.vo.TokenVO;
import com.japy.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证接口：注册 / 登录 / 刷新 / 登出 / 当前用户信息 / 菜单路由
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MenuTreeService menuTreeService;

    @PostMapping("/register")
    @RateLimit(permitsPerSecond = 3, key = "register")
    public R<TokenVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        return R.ok(authService.register(dto, request));
    }

    @PostMapping("/login")
    @RateLimit(permitsPerSecond = 3, key = "login")
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

    /**
     * 当前登录用户信息（若依 getInfo）：user + roles + permissions。
     * permissions 供前端按钮级校验（v-perm）；admin 为通配 *:*:*。
     */
    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        LoginUser loginUser = currentLoginUser();
        Map<String, Object> data = new HashMap<>();
        data.put("user", authService.userInfoVO(loginUser.getUserId()));
        data.put("roles", loginUser.getRoles());
        data.put("permissions", loginUser.getPerms());
        return R.ok(data);
    }

    /**
     * 当前用户可见菜单路由树（若依 getRouters）：前端按此动态 addRoute。
     */
    @GetMapping("/routers")
    public R<List<RouterVo>> routers() {
        LoginUser loginUser = currentLoginUser();
        boolean isAdmin = loginUser.getRoles().contains("admin");
        List<RouterVo> tree = menuTreeService.buildRouterTree(
                menuTreeService.listMenusByUser(loginUser.getUserId(), isAdmin));
        return R.ok(tree);
    }

    private LoginUser currentLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new com.japy.common.BusinessException("未登录");
    }
}
