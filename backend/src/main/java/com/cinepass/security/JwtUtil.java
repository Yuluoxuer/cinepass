package com.cinepass.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
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
import java.util.UUID;

/**
 * JWT 工具（HS256）。Claims：{@code sub}(userId) / {@code role} / {@code cinemaId} / {@code sid} / {@code jti} / {@code exp}。
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expire-seconds:3600}")
    private long accessTokenExpireSeconds;

    /** 获取签名密钥；不足 32 字节时右侧补零以满足 HS256 要求 */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** 签发 Access Token（无 cinemaId） */
    public String generateAccessToken(String userId, String username, String role, String sid) {
        return generateAccessToken(userId, username, role, sid, null);
    }

    /** 签发 Access Token，写入 userId、昵称、角色、可选影院与会话 sid */
    public String generateAccessToken(String userId, String username, String role, String sid, String cinemaId) {
        Date now = new Date();
        String jti = UUID.randomUUID().toString().replace("-", "");
        String normalizedRole = role != null ? role : Roles.USER;
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .setSubject(userId)
                .setId(jti)
                .claim("username", username)
                .claim("role", normalizedRole)
                .claim("roles", Collections.singletonList(normalizedRole))
                .claim("sid", sid)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessTokenExpireSeconds * 1000L));
        // 空串不当作有效影院绑定，避免 claim 解析成 ""
        if (cinemaId != null && !cinemaId.isEmpty()) {
            builder.claim("cinemaId", cinemaId);
        }
        return builder.signWith(getSigningKey(), SignatureAlgorithm.HS256).compact();
    }

    /** 兼容重载：从角色列表取主角色并自动生成 sid */
    public String generateAccessToken(String userId, String username, List<String> roles) {
        String role = (roles != null && !roles.isEmpty()) ? roles.get(0) : Roles.USER;
        return generateAccessToken(userId, username, role, UUID.randomUUID().toString().replace("-", ""), null);
    }

    /** 解析 Token 中的 userId（sub）；失败返回 null */
    public String parseUserId(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.warn("Token parse userId failed: {}", e.getMessage());
            return null;
        }
    }

    /** 解析 Access Token 为用户信息；过期或非法返回 null */
    public JwtUserInfo parseAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return toUserInfo(claims);
        } catch (Exception e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    /** 解析 Access Token，允许已过期（用于静默续期 / 登出） */
    public JwtUserInfo parseAccessTokenAllowExpired(String token) {
        try {
            return toUserInfo(parseClaimsAllowExpired(token));
        } catch (Exception e) {
            log.warn("JWT parse(allow expired) failed: {}", e.getMessage());
            return null;
        }
    }

    /** 校验 Token 是否合法且未过期 */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 仅校验签名，忽略过期 */
    public boolean isSignatureValid(String token) {
        try {
            parseClaimsAllowExpired(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 判断 Token 是否已过期；签名非法时返回 false */
    public boolean isExpired(String token) {
        try {
            parseClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 返回 Access Token 有效期（秒） */
    public long getExpiresInSeconds() {
        return accessTokenExpireSeconds;
    }

    /** 从 Claims 提取角色列表（当前为单角色包装） */
    public List<String> getRoles(Claims claims) {
        if (claims == null) {
            return Collections.emptyList();
        }
        String role = resolveRole(claims);
        return role != null ? Collections.singletonList(role) : Collections.<String>emptyList();
    }

    /** 从 Claims 解析主角色，缺省为 user */
    @SuppressWarnings("unchecked")
    private String resolveRole(Claims claims) {
        String role = claims.get("role", String.class);
        if (role != null && !role.isEmpty()) {
            return role;
        }
        List<String> roles = claims.get("roles", List.class);
        if (roles != null && !roles.isEmpty()) {
            return roles.get(0);
        }
        return Roles.USER;
    }

    /** 严格解析 Claims（过期会抛异常） */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** 解析 Claims，过期时仍返回其中内容 */
    private Claims parseClaimsAllowExpired(String token) {
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    /** Claims 转为 JwtUserInfo */
    private JwtUserInfo toUserInfo(Claims claims) {
        if (claims == null) {
            return null;
        }
        return new JwtUserInfo(
                claims.getSubject(),
                claims.get("username", String.class),
                resolveRole(claims),
                claims.get("cinemaId", String.class),
                claims.get("sid", String.class),
                claims.getId());
    }

    /** Access Token 解析结果：用户身份与会话标识 */
    public static class JwtUserInfo {
        private final String userId;
        private final String username;
        private final String role;
        private final String cinemaId;
        private final String sid;
        private final String jti;

        public JwtUserInfo(String userId, String username, String role, String cinemaId, String sid, String jti) {
            this.userId = userId;
            this.username = username;
            this.role = role != null ? role : Roles.USER;
            this.cinemaId = cinemaId;
            this.sid = sid;
            this.jti = jti;
        }

        public String getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }

        /** staff 所属影院；其余可为 null */
        public String getCinemaId() {
            return cinemaId;
        }

        public List<String> getRoles() {
            return Collections.singletonList(role);
        }

        public String getSid() {
            return sid;
        }

        public String getJti() {
            return jti;
        }
    }
}
