package com.japy.common;

import com.japy.entity.User;
import com.japy.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    public AuthInterceptor(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        // 公开路径：认证接口 + GET浏览（时间线/评论/个人主页）
        if (path.startsWith("/auth/")
                || (path.startsWith("/api/moments") && "GET".equalsIgnoreCase(request.getMethod()))
                || (path.startsWith("/api/comments") && "GET".equalsIgnoreCase(request.getMethod()))
                || (path.matches("/api/users/\\d+") && "GET".equalsIgnoreCase(request.getMethod()))) {
            // 尝试解析token（可选），有就设置上下文
            trySetUser(request);
            return true;
        }

        // 其他接口必须登录
        String token = extractToken(request);
        if (token == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\"}");
            return false;
        }
        try {
            Claims claims = JwtUtil.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            // 检查封禁状态
            User user = userMapper.selectById(userId);
            if (user == null || user.getStatus() == 1) {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"msg\":\"账号已被封禁\"}");
                return false;
            }
            // 管理端路径需要 admin 角色
            if (path.startsWith("/api/admin/") && !"admin".equals(user.getRole())) {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"msg\":\"需要管理员权限\"}");
                return false;
            }
            UserContext.set(userId, user.getNickname(), user.getRole());
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"token无效或已过期\"}");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void trySetUser(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            try {
                Claims claims = JwtUtil.parse(token);
                Long userId = Long.valueOf(claims.getSubject());
                User user = userMapper.selectById(userId);
                if (user != null && user.getStatus() == 0) {
                    // 昵称以库中最新为准（管理员强改昵称后立即生效）
                    UserContext.set(userId, user.getNickname(), user.getRole());
                }
            } catch (Exception ignored) {}
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
