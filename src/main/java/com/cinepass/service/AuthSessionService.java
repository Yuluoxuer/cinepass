package com.cinepass.service;

import com.alibaba.fastjson2.JSON;
import com.cinepass.constant.CacheKeys;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthSessionService {

    private final StringRedisTemplate redis;
    private final long refreshExpireSeconds;

    public AuthSessionService(StringRedisTemplate redis,
                              @Value("${jwt.refresh-expire-seconds:604800}") long refreshExpireSeconds) {
        this.redis = redis;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    public void saveRefresh(String sid, String userId, String role) {
        try {
            RefreshSession session = new RefreshSession();
            session.setUserId(userId);
            session.setRole(role);
            session.setRefreshJti(UUID.randomUUID().toString().replace("-", ""));
            redis.opsForValue().set(CacheKeys.refreshTokenKey(sid), JSON.toJSONString(session),
                    refreshExpireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[AuthSession] Redis 不可用，跳过 refresh session 持久化: {}", e.getMessage());
        }
    }

    public Optional<RefreshSession> getRefresh(String sid) {
        if (sid == null) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(CacheKeys.refreshTokenKey(sid));
            if (json == null || json.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(JSON.parseObject(json, RefreshSession.class));
        } catch (Exception e) {
            log.warn("[AuthSession] Redis 不可用，无法读取 refresh session: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void deleteRefresh(String sid) {
        if (sid == null) return;
        try {
            redis.delete(CacheKeys.refreshTokenKey(sid));
        } catch (Exception e) {
            log.warn("[AuthSession] Redis 不可用，跳过删除 refresh session: {}", e.getMessage());
        }
    }

    public void denyJti(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) return;
        try {
            redis.opsForValue().set(CacheKeys.denyJtiKey(jti), "1", ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[AuthSession] Redis 不可用，跳过 JTI 黑名单: {}", e.getMessage());
        }
    }

    public boolean isDenied(String jti) {
        if (jti == null) return false;
        try {
            Boolean has = redis.hasKey(CacheKeys.denyJtiKey(jti));
            return Boolean.TRUE.equals(has);
        } catch (Exception e) {
            return false;
        }
    }

    public long getRefreshExpireSeconds() {
        return refreshExpireSeconds;
    }

    @Data
    public static class RefreshSession {
        private String userId;
        private String role;
        private String refreshJti;
    }
}
