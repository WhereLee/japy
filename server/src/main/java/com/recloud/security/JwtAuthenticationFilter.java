package com.recloud.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * JWT 认证过滤器
 * <p>
 * 认证流程：JWT 签名验证（本地） → Redis 黑名单检查（降级跳过）→ Redis 用户缓存（降级用 JWT claims 构建）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String USER_LOGIN_PREFIX = "user:login:";
    private static final String USER_DISABLED_PREFIX = "user:disabled:";

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            // 1. 黑名单检查（Redis 不可用时降级跳过）
            if (isBlacklisted(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 2. 用户禁用状态实时检查（管理员禁用后立即生效）
            Long checkUserId = jwtTokenProvider.getUserId(token);
            if (isUserDisabled(checkUserId)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 3. 构建认证信息
            LoginUser loginUser = loadLoginUser(token);
            if (loginUser != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 检查用户是否被禁用（Redis 标记，管理员禁用时写入）
     * Redis 不可用时降级跳过（依赖 JWT 中的 status 字段）
     */
    private boolean isUserDisabled(Long userId) {
        try {
            Boolean result = redisTemplate.hasKey(USER_DISABLED_PREFIX + userId);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Redis 用户禁用检查降级，跳过: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 Token 是否在黑名单中
     * Redis 不可用时降级为"未拉黑"（JWT 过期后自然失效）
     * 使用 Token 的 SHA-256 哈希作为 key，避免 key 过长
     */
    private boolean isBlacklisted(String token) {
        try {
            Boolean result = redisTemplate.hasKey(BLACKLIST_PREFIX + hashToken(token));
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Redis 黑名单检查降级，跳过: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Token 哈希（SHA-256），避免 Redis key 过长
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return token;
        }
    }

    /**
     * 加载用户信息
     * 优先从 Redis 缓存获取，Redis 不可用时从 JWT claims 构建（携带 role/status）
     */
    private LoginUser loadLoginUser(String token) {
        Long userId = jwtTokenProvider.getUserId(token);

        // 尝试从 Redis 获取完整用户信息
        try {
            String userJson = redisTemplate.opsForValue().get(USER_LOGIN_PREFIX + userId);
            if (userJson != null) {
                return objectMapper.readValue(userJson, LoginUser.class);
            }
        } catch (Exception e) {
            log.warn("Redis 用户缓存获取降级，回退到 JWT: {}", e.getMessage());
        }

        // 降级：从 JWT claims 构建（携带 role 和 status，而非硬编码）
        try {
            String username = jwtTokenProvider.getUsername(token);
            String role = jwtTokenProvider.getRole(token);
            Integer status = jwtTokenProvider.getStatus(token);
            return new LoginUser(userId, username, null,
                    status != null ? status : 1,
                    role != null ? role : "user");
        } catch (Exception e) {
            log.warn("降级构建用户信息失败，拒绝访问: {}", e.getMessage());
            return null;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
