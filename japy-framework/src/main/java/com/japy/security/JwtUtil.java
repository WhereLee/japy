package com.japy.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：双 token 体系
 * - access token：短时（2h），携带 userId/username，用于接口认证
 * - refresh token：长时（7d），携带 userId，用于刷新 access 与登出失效（配合 Redis 会话）
 */
@Component
public class JwtUtil {

    private SecretKey key;
    private long accessExpire;
    private long refreshExpire;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-expire}") long accessExpire,
                   @Value("${jwt.refresh-expire}") long refreshExpire) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpire = accessExpire;
        this.refreshExpire = refreshExpire;
    }

    public String createAccessToken(Long userId, String username) {
        return createToken(userId, username, accessExpire, "access");
    }

    public String createRefreshToken(Long userId, String username) {
        return createToken(userId, username, refreshExpire, "refresh");
    }

    private String createToken(Long userId, String username, long expireSec, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSec * 1000))
                .signWith(key)
                .compact();
    }

    /** 解析并校验 token（过期/签名错误返回 null） */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public long getAccessExpire() { return accessExpire; }
}
