package com.minihr.security; // 权限包

import io.jsonwebtoken.Claims;              // JWT 载荷
import io.jsonwebtoken.Jwts;                // 签发/解析入口
import io.jsonwebtoken.SignatureAlgorithm;  // HS256
import io.jsonwebtoken.security.Keys;       // HMAC 密钥
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
 * JWT 工具 — HS256。Claims：sub/role/sid/jti/exp，TTL 默认 3600s。
 * <p>
 * 调用方：JwtAuthFilter（parse/validate）；登录 Service（generate）。
 * Glob：已有 JwtUtil.java，覆盖加注释。无数据文件（密钥来自 jwt.secret 配置）。
 * 用户指令：「给代码每行加上详细的注释」
 */
@Slf4j
@Component
public class JwtUtil {

    /** 签名密钥，来自 jwt.secret */
    @Value("${jwt.secret}")
    private String secret;

    /** Access 有效秒数，默认 3600 */
    @Value("${jwt.access-token-expire-seconds:3600}")
    private long accessTokenExpireSeconds;

    /** 配置字符串 → HMAC 密钥；不足 32 字节则右侧补 0 */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8); // 字符串转字节
        if (keyBytes.length < 32) { // HS256 最少 256 bit
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发 Access Token。
     *
     * @param userId   → sub
     * @param username 展示名
     * @param role     user/staff/admin
     * @param sid      会话 ID（Refresh 索引）
     */
    public String generateAccessToken(Long userId, String username, String role, String sid) {
        Date now = new Date(); // iat
        String jti = UUID.randomUUID().toString().replace("-", ""); // Token 唯一号
        String normalizedRole = role != null ? role : Roles.USER; // 空则 user
        return Jwts.builder()
                .setSubject(String.valueOf(userId)) // sub
                .setId(jti)                         // jti
                .claim("username", username)
                .claim("role", normalizedRole)      // 系分主字段
                .claim("roles", Collections.singletonList(normalizedRole)) // 兼容旧字段
                .claim("sid", sid)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessTokenExpireSeconds * 1000L))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact(); // 最终 JWT 字符串
    }

    /** 旧接口：roles 列表取第一个，自动生成 sid */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        String role = (roles != null && !roles.isEmpty()) ? roles.get(0) : Roles.USER;
        return generateAccessToken(userId, username, role, UUID.randomUUID().toString().replace("-", ""));
    }

    /** 只取 userId；失败返回 null */
    public Long parseUserId(String token) {
        try {
            Claims claims = parseClaims(token);
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            log.warn("Token parse userId failed: {}", e.getMessage());
            return null;
        }
    }

    /** 解析完整用户信息；失败返回 null */
    public JwtUserInfo parseAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            String role = resolveRole(claims);
            String sid = claims.get("sid", String.class);
            String jti = claims.getId();
            return new JwtUserInfo(userId, username, role, sid, jti);
        } catch (Exception e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    /** 验签且未过期 → true */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 配置的过期秒数 */
    public long getExpiresInSeconds() {
        return accessTokenExpireSeconds;
    }

    /** Claims → 角色列表 */
    public List<String> getRoles(Claims claims) {
        if (claims == null) {
            return Collections.emptyList();
        }
        String role = resolveRole(claims);
        return role != null ? Collections.singletonList(role) : Collections.<String>emptyList();
    }

    /** 优先 role，其次 roles[0]，默认 user */
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

    /** 验签并返回载荷 */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** Filter 使用的用户快照 */
    public static class JwtUserInfo {
        private final Long userId;
        private final String username;
        private final String role;
        private final String sid;
        private final String jti;

        public JwtUserInfo(Long userId, String username, String role, String sid, String jti) {
            this.userId = userId;
            this.username = username;
            this.role = role != null ? role : Roles.USER;
            this.sid = sid;
            this.jti = jti;
        }

        public Long getUserId() {
            return userId; // JWT sub
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role; // user/staff/admin
        }

        /** 单角色包成 List，兼容旧调用 */
        public List<String> getRoles() {
            return Collections.singletonList(role);
        }

        public String getSid() {
            return sid; // 会话
        }

        public String getJti() {
            return jti; // Token 编号
        }
    }
}
