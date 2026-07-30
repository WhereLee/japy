package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import com.recloud.dto.response.AuthResponse;
import com.recloud.entity.User;
import com.recloud.mapper.UserMapper;
import com.recloud.security.JwtTokenProvider;
import com.recloud.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务
 * <p>
 * 核心功能：注册、登录、Token 刷新、登出。
 * 安全设计：
 * - 密码 BCrypt 加密存储
 * - 登录失败 5 次锁定 15 分钟（Redis 计数器）
 * - Refresh Token 轮换（旧 token 加入黑名单）
 * - 登出时 Token 加入 Redis 黑名单，TTL = Token 剩余有效时间
 * - JWT claims 中携带 role/status，Redis 不可用时可降级读取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String LOGIN_FAIL_PREFIX = "login_fail:";
    private static final String LOGIN_LOCK_PREFIX = "login_lock:";
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String USER_LOGIN_PREFIX = "user:login:";
    private static final int MAX_LOGIN_FAIL = 5;
    private static final long LOCK_MINUTES = 15;

    /**
     * 注册
     */
    public AuthResponse register(String username, String nickname, String rawPassword) {
        // 检查用户名是否已存在
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (existing != null) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("user");
        user.setStatus(1);
        userMapper.insert(user);

        return buildAuthResponse(user);
    }

    /**
     * 登录
     */
    public AuthResponse login(String username, String rawPassword) {
        // 1. 检查账号是否被锁定
        checkAccountLock(username);

        // 2. 查找用户（显式 select password 字段，因为 @TableField(select=false) 默认不查询密码）
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getUsername, User::getPassword,
                                User::getNickname, User::getRole, User::getStatus,
                                User::getLoginFailCount, User::getLockTime, User::getLastLoginAt)
                        .eq(User::getUsername, username)
        );
        if (user == null) {
            throw new BizException(ResultCode.LOGIN_FAIL);
        }

        // 3. 检查账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ResultCode.USER_DISABLED);
        }

        // 4. 验证密码
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            // 密码错误，增加失败计数
            incrementLoginFail(username);
            throw new BizException(ResultCode.LOGIN_FAIL);
        }

        // 5. 登录成功，清除失败计数
        clearLoginFail(username);

        return buildAuthResponse(user);
    }

    /**
     * 刷新 Token
     * <p>
     * Refresh Token 轮换：验证旧 refresh token → 签发新双 Token → 旧 refresh token 加入黑名单
     */
    public AuthResponse refreshToken(String refreshToken) {
        // 1. 验证 refresh token（校验 type == "refresh"，防止 Access Token 调刷新接口）
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // 2. 检查是否在黑名单
        if (isBlacklisted(refreshToken)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // 3. 获取用户信息
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException(ResultCode.USER_DISABLED);
        }

        // 4. 旧 refresh token 加入黑名单
        addToBlacklist(refreshToken);

        // 5. 签发新双 Token
        return buildAuthResponse(user);
    }

    /**
     * 登出
     * <p>
     * 将 access token 和 refresh token 都加入黑名单，TTL = Token 剩余有效时间。
     * 同时清除 Redis 中的用户缓存。
     */
    public void logout(String accessToken, String refreshToken, Long userId) {
        // Token 加入黑名单
        if (accessToken != null) {
            addToBlacklist(accessToken);
        }
        if (refreshToken != null) {
            addToBlacklist(refreshToken);
        }
        // 清除用户缓存
        if (userId != null) {
            try {
                redisTemplate.delete(USER_LOGIN_PREFIX + userId);
            } catch (Exception e) {
                log.warn("清除用户缓存失败: {}", e.getMessage());
            }
        }
    }

    // ==================== 内部方法 ====================

    private AuthResponse buildAuthResponse(User user) {
        // JWT claims 中携带 role 和 status，Redis 不可用时可降级读取
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole(), user.getStatus());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 缓存用户信息到 Redis
        cacheUserInfo(user);

        return new AuthResponse(
                accessToken, refreshToken,
                user.getId(), user.getUsername(), user.getNickname(), user.getRole()
        );
    }

    private void cacheUserInfo(User user) {
        try {
            LoginUser loginUser = new LoginUser(
                    user.getId(), user.getUsername(), null,
                    user.getStatus(), user.getRole()
            );
            String json = objectMapper.writeValueAsString(loginUser);
            redisTemplate.opsForValue().set(USER_LOGIN_PREFIX + user.getId(), json, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("缓存用户信息失败: {}", e.getMessage());
        }
    }

    private void checkAccountLock(String username) {
        try {
            Boolean locked = redisTemplate.hasKey(LOGIN_LOCK_PREFIX + username);
            if (Boolean.TRUE.equals(locked)) {
                throw new BizException(ResultCode.ACCOUNT_LOCKED);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("检查账号锁定状态失败，降级放行: {}", e.getMessage());
        }
    }

    private void incrementLoginFail(String username) {
        try {
            String key = LOGIN_FAIL_PREFIX + username;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, LOCK_MINUTES, TimeUnit.MINUTES);
            }
            if (count != null && count >= MAX_LOGIN_FAIL) {
                // 锁定账号
                redisTemplate.opsForValue().set(LOGIN_LOCK_PREFIX + username, "locked", LOCK_MINUTES, TimeUnit.MINUTES);
                redisTemplate.delete(key);
                log.info("账号 {} 因连续 {} 次登录失败被锁定 {} 分钟", username, MAX_LOGIN_FAIL, LOCK_MINUTES);
            }
        } catch (Exception e) {
            log.warn("登录失败计数异常，降级: {}", e.getMessage());
        }
    }

    private void clearLoginFail(String username) {
        try {
            redisTemplate.delete(LOGIN_FAIL_PREFIX + username);
        } catch (Exception e) {
            log.warn("清除登录失败计数异常: {}", e.getMessage());
        }
    }

    private void addToBlacklist(String token) {
        try {
            long remaining = jwtTokenProvider.getRemainingSeconds(token);
            if (remaining > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + hashToken(token), "1", remaining, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Token 加入黑名单失败: {}", e.getMessage());
        }
    }

    private boolean isBlacklisted(String token) {
        try {
            Boolean result = redisTemplate.hasKey(BLACKLIST_PREFIX + hashToken(token));
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Redis 黑名单检查降级: {}", e.getMessage());
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
            // SHA-256 不存在时回退到 token 本身
            return token;
        }
    }
}
