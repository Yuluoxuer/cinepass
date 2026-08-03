package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.constant.CacheKeys;
import com.cinepass.service.AuthSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * {@link AuthSessionService} 实现。
 */
@Service
public class AuthSessionServiceImpl implements AuthSessionService {

    private final StringRedisTemplate redis;
    private final long refreshExpireSeconds;

    public AuthSessionServiceImpl(StringRedisTemplate redis,
                                  @Value("${jwt.refresh-expire-seconds:604800}") long refreshExpireSeconds) {
        this.redis = redis;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    @Override
    public void saveRefresh(String sid, String userId, String role) {
        RefreshSession session = new RefreshSession();
        session.setUserId(userId);
        session.setRole(role);
        session.setRefreshJti(UUID.randomUUID().toString().replace("-", ""));
        redis.opsForValue().set(CacheKeys.refreshTokenKey(sid), JSON.toJSONString(session),
                refreshExpireSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Optional<RefreshSession> getRefresh(String sid) {
        if (sid == null) {
            return Optional.empty();
        }
        String json = redis.opsForValue().get(CacheKeys.refreshTokenKey(sid));
        if (json == null || json.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(JSON.parseObject(json, RefreshSession.class));
    }

    @Override
    public void deleteRefresh(String sid) {
        if (sid != null) {
            redis.delete(CacheKeys.refreshTokenKey(sid));
        }
    }

    @Override
    public void denyJti(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) {
            return;
        }
        redis.opsForValue().set(CacheKeys.denyJtiKey(jti), "1", ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean isDenied(String jti) {
        if (jti == null) {
            return false;
        }
        Boolean has = redis.hasKey(CacheKeys.denyJtiKey(jti));
        return Boolean.TRUE.equals(has);
    }

    @Override
    public long getRefreshExpireSeconds() {
        return refreshExpireSeconds;
    }
}
