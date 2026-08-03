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
 * JWT 工具 — HS256。Claims：sub(userId 字符串)/role/sid/jti/exp。
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expire-seconds:3600}")
    private long accessTokenExpireSeconds;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(String userId, String username, String role, String sid) {
        return generateAccessToken(userId, username, role, null, sid);
    }

    public String generateAccessToken(String userId, String username, String role, String cinemaId, String sid) {
        Date now = new Date();
        String jti = UUID.randomUUID().toString().replace("-", "");
        String normalizedRole = role != null ? role : Roles.USER;
        return Jwts.builder()
                .setSubject(userId)
                .setId(jti)
                .claim("username", username)
                .claim("role", normalizedRole)
                .claim("roles", Collections.singletonList(normalizedRole))
                .claim("cinemaId", cinemaId)
                .claim("sid", sid)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessTokenExpireSeconds * 1000L))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateAccessToken(String userId, String username, List<String> roles) {
        String role = (roles != null && !roles.isEmpty()) ? roles.get(0) : Roles.USER;
        return generateAccessToken(userId, username, role, null, UUID.randomUUID().toString().replace("-", ""));
    }

    public String parseUserId(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.warn("Token parse userId failed: {}", e.getMessage());
            return null;
        }
    }

    public JwtUserInfo parseAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return toUserInfo(claims);
        } catch (Exception e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    /** 验签；允许过期（静默续期 / 登出）。 */
    public JwtUserInfo parseAccessTokenAllowExpired(String token) {
        try {
            return toUserInfo(parseClaimsAllowExpired(token));
        } catch (Exception e) {
            log.warn("JWT parse(allow expired) failed: {}", e.getMessage());
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

    public boolean isSignatureValid(String token) {
        try {
            parseClaimsAllowExpired(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

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

    public long getExpiresInSeconds() {
        return accessTokenExpireSeconds;
    }

    public List<String> getRoles(Claims claims) {
        if (claims == null) {
            return Collections.emptyList();
        }
        String role = resolveRole(claims);
        return role != null ? Collections.singletonList(role) : Collections.<String>emptyList();
    }

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

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Claims parseClaimsAllowExpired(String token) {
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

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
