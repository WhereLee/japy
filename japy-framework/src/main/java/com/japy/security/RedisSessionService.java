package com.japy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 会话管理：
 * - 登录成功后缓存 LoginUser（key: login:user:{userId}），在线用户列表依据此
 * - refresh token 存储（key: login:refresh:{userId}），登出/强退时删除 → token 立即失效
 * - 登录失败计数（key: login:fail:{username}）防爆破
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSessionService {

    private static final String USER_KEY = "login:user:";
    private static final String REFRESH_KEY = "login:refresh:";
    private static final String FAIL_KEY = "login:fail:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 登录成功：写会话与 refresh token */
    public void saveSession(LoginUser loginUser, String refreshToken, long refreshExpireSec) {
        try {
            redis.opsForValue().set(USER_KEY + loginUser.getUserId(),
                    objectMapper.writeValueAsString(loginUser), Duration.ofSeconds(refreshExpireSec));
            redis.opsForValue().set(REFRESH_KEY + loginUser.getUserId(),
                    refreshToken, Duration.ofSeconds(refreshExpireSec));
        } catch (Exception e) {
            log.error("会话写入失败", e);
        }
    }

    /** 从 Redis 加载会话（认证过滤器用） */
    public LoginUser getLoginUser(Long userId) {
        try {
            String json = redis.opsForValue().get(USER_KEY + userId);
            return json == null ? null : objectMapper.readValue(json, LoginUser.class);
        } catch (Exception e) {
            log.error("会话读取失败", e);
            return null;
        }
    }

    /** 校验 refresh token 是否有效（刷新 access 时） */
    public boolean checkRefreshToken(Long userId, String refreshToken) {
        String saved = redis.opsForValue().get(REFRESH_KEY + userId);
        return saved != null && saved.equals(refreshToken);
    }

    /** 登出 / 强退：删除会话（token 立即失效） */
    public void removeSession(Long userId) {
        redis.delete(USER_KEY + userId);
        redis.delete(REFRESH_KEY + userId);
    }

    /** 记录登录失败次数（按 账号+IP 复合维度，防攻击者锁定他人账号），返回当前次数 */
    public long incrFailCount(String username, String ip, Duration expire) {
        String key = FAIL_KEY + username + ":" + ip;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, expire);
        }
        return count == null ? 0 : count;
    }

    public void clearFailCount(String username, String ip) {
        redis.delete(FAIL_KEY + username + ":" + ip);
    }
}
