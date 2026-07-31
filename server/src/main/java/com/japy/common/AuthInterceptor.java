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
        // 公开路径：认证接口 + 静态资源 + GET浏览
        if (path.startsWith("/auth/") || path.startsWith("/api/novels")
                || (path.startsWith("/api/posts") && "GET".equalsIgnoreCase(request.getMethod()))
                || (path.startsWith("/api/comments") && "GET".equalsIgnoreCase(request.getMethod()))) {
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
            String nickname = claims.get("nickname", String.class);
            // 检查封禁状态
            User user = userMapper.selectById(userId);
            if (user == null || user.getStatus() == 1) {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"msg\":\"账号已被封禁\"}");
                return false;
            }
            UserContext.set(userId, nickname);
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
                UserContext.set(Long.valueOf(claims.getSubject()), claims.get("nickname", String.class));
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
