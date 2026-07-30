package com.recloud.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT Token 工具类
 * <p>
 * 双 Token 机制：
 * - Access Token：短有效期（30min），携带用户基本信息，用于接口认证
 * - Refresh Token：长有效期（7d），仅用于刷新 Access Token，不直接用于接口认证
 * <p>
 * 安全设计：
 * - 密钥 Base64 编码，至少 256 位
 * Token 剩余有效时间用于黑名单 TTL，避免黑名单 TTL 超过 Token 实际有效期
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Access Token
     */
    public String generateToken(Long userId, String username, String role, Integer status) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .claim("status", status)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成 Refresh Token
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从 Token 获取用户 ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 获取用户名
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 Token 获取角色
     */
    public String getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 从 Token 获取状态
     */
    public Integer getStatus(String token) {
        Claims claims = parseToken(token);
        return claims.get("status", Integer.class);
    }

    /**
     * 获取 Token 剩余有效时间（秒）
     * 用于登出时设置黑名单 TTL，避免黑名单 TTL 超过 Token 实际有效期
     */
    public long getRemainingSeconds(String token) {
        try {
            Claims claims = parseToken(token);
            long expMs = claims.getExpiration().getTime();
            long remaining = (expMs - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证 Refresh Token 是否有效（校验 type == "refresh"）
     * 防止攻击者用 Access Token 调刷新接口
     */
    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
