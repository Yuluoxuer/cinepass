package com.minihr.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类 — HS256，accessToken 30min，refreshToken 7d
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private static final long ACCESS_TOKEN_EXPIRE_MS = 30 * 60 * 1000L;
    private static final long REFRESH_TOKEN_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ACCESS_TOKEN_EXPIRE_MS))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + REFRESH_TOKEN_EXPIRE_MS))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Long parseUserId(String token) {
        try {
            Claims claims = parseClaims(token);
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            log.warn("Token parse userId failed: {}", e.getMessage());
            return null;
        }
    }

    public JwtUserInfo parseAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            return new JwtUserInfo(userId, username, roles);
        } catch (Exception e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpiresInSeconds() {
        return ACCESS_TOKEN_EXPIRE_MS / 1000;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        if (claims == null) {
            return Collections.emptyList();
        }
        List<String> roles = claims.get("roles", List.class);
        return roles != null ? roles : Collections.<String>emptyList();
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static class JwtUserInfo {
        private final Long userId;
        private final String username;
        private final List<String> roles;

        public JwtUserInfo(Long userId, String username, List<String> roles) {
            this.userId = userId;
            this.username = username;
            this.roles = roles;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public List<String> getRoles() {
            return roles;
        }
    }
}
