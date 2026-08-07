package com.cinepass.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

/**
 * 支付/核销二维码 token 工具（系分 §3.2.6）。
 * <p>独立签名密钥 {@code ticket.pay-qr-secret}；claim 仅含 jti/orderId/userId/exp。
 * 签发后由调用方写 Redis {@code pay:token:{jti}} / {@code redeem:token:{jti}} 做防重放
 * （确认后按 jti DEL），JWT 自身 {@code exp} 为过期兜底。</p>
 */
@Component
public class PayTokenUtil {

    /** 二维码有效期上限：now + 900s（与锁座 TTL 对齐） */
    static final long DEFAULT_TTL_SECONDS = 900;

    @Value("${ticket.pay-qr-secret}")
    private String secret;

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

    /**
     * 签发二维码 token：{@code exp} = min(订单支付截止, now + 900s)。
     *
     * @param jtiPrefix      jti 前缀（支付用 {@code pt_}，核销用 {@code rt_}，区分 Redis 命名空间）
     * @param orderId        订单 ID
     * @param userId         订单归属用户
     * @param orderExpireAt  订单支付截止（锁座过期时间）；核销场景可传 null
     * @return token 与 jti（Redis 防重放键）及剩余有效秒数（Redis TTL）
     */
    public IssueResult issue(String jtiPrefix, String orderId, String userId, OffsetDateTime orderExpireAt) {
        Date now = new Date();
        long nowSec = now.getTime() / 1000L;
        long orderExpireSec = orderExpireAt == null
                ? Long.MAX_VALUE
                : orderExpireAt.toEpochSecond();
        long expSec = Math.min(orderExpireSec, nowSec + DEFAULT_TTL_SECONDS);
        long ttlSeconds = Math.max(expSec - nowSec, 1);

        String jti = jtiPrefix + UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .setId(jti)
                .claim("orderId", orderId)
                .claim("userId", userId)
                .setIssuedAt(now)
                .setExpiration(new Date(expSec * 1000L))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
        return new IssueResult(token, jti, ttlSeconds);
    }

    /**
     * 校验签名并解析 token；非法返回 null（调用方再区分 orderId 不匹配与过期）。
     */
    public PayTokenPayload verify(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return new PayTokenPayload(
                    claims.getId(),
                    claims.get("orderId", String.class),
                    claims.get("userId", String.class),
                    claims.getExpiration());
        } catch (Exception e) {
            return null;
        }
    }

    /** 签发结果：JWT 字符串 + jti + 剩余有效秒数 */
    public static class IssueResult {

        private final String token;
        private final String jti;
        private final long ttlSeconds;

        public IssueResult(String token, String jti, long ttlSeconds) {
            this.token = token;
            this.jti = jti;
            this.ttlSeconds = ttlSeconds;
        }

        public String getToken() {
            return token;
        }

        public String getJti() {
            return jti;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }
    }

    /** 解析结果：jti / orderId / userId / 过期时间 */
    public static class PayTokenPayload {

        private final String jti;
        private final String orderId;
        private final String userId;
        private final Date exp;

        public PayTokenPayload(String jti, String orderId, String userId, Date exp) {
            this.jti = jti;
            this.orderId = orderId;
            this.userId = userId;
            this.exp = exp;
        }

        public String getJti() {
            return jti;
        }

        public String getOrderId() {
            return orderId;
        }

        public String getUserId() {
            return userId;
        }

        public Date getExp() {
            return exp;
        }
    }
}
