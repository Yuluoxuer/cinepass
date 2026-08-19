package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.constant.CacheKeys;
import com.cinepass.mapper.AuthRefreshSessionMapper;
import com.cinepass.model.AuthRefreshSession;
import com.cinepass.service.AuthSessionService;
import com.cinepass.util.DateTimeFormats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * {@link AuthSessionService} 实现：Refresh 会话 DB 真相 + Redis 缓存；deny jti 仅 Redis 且异常 fail-open。
 */
@Slf4j
@Service
public class AuthSessionServiceImpl implements AuthSessionService {

    private final StringRedisTemplate redis;
    private final AuthRefreshSessionMapper sessionMapper;
    private final long refreshExpireSeconds;

    public AuthSessionServiceImpl(StringRedisTemplate redis,
                                  AuthRefreshSessionMapper sessionMapper,
                                  @Value("${jwt.refresh-expire-seconds:604800}") long refreshExpireSeconds) {
        this.redis = redis;
        this.sessionMapper = sessionMapper;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    /** 登录成功：先落库，再 best-effort 写 Redis */
    @Override
    public void saveRefresh(String sid, String userId, String role) {
        OffsetDateTime now = DateTimeFormats.now();
        String refreshJti = UUID.randomUUID().toString().replace("-", "");

        AuthRefreshSession row = new AuthRefreshSession();
        row.setSid(sid);
        row.setUserId(userId);
        row.setRole(role);
        row.setRefreshJti(refreshJti);
        row.setExpireAt(now.plusSeconds(refreshExpireSeconds));
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        sessionMapper.upsert(row);

        RefreshSession session = toRefreshSession(row);
        cacheRefresh(sid, session);
    }

    /** Redis 优先；miss/异常回落 DB，命中则回填 Redis */
    @Override
    public Optional<RefreshSession> getRefresh(String sid) {
        if (sid == null) {
            return Optional.empty();
        }
        Optional<RefreshSession> cached = getFromRedis(sid);
        if (cached.isPresent()) {
            return cached;
        }
        AuthRefreshSession row = sessionMapper.findValidBySid(sid, DateTimeFormats.now());
        if (row == null) {
            return Optional.empty();
        }
        RefreshSession session = toRefreshSession(row);
        cacheRefresh(sid, session);
        return Optional.of(session);
    }

    /** 登出：先删库，再 best-effort 删 Redis */
    @Override
    public void deleteRefresh(String sid) {
        if (sid == null) {
            return;
        }
        sessionMapper.deleteBySid(sid);
        try {
            redis.delete(CacheKeys.refreshTokenKey(sid));
        } catch (Exception e) {
            log.warn("deleteRefresh Redis fail sid={}: {}", sid, e.getMessage());
        }
    }

    /** Access jti 拉黑；Redis 异常仅记日志，不阻断登出 */
    @Override
    public void denyJti(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) {
            return;
        }
        try {
            redis.opsForValue().set(CacheKeys.denyJtiKey(jti), "1", ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("denyJti Redis fail jti={}: {}", jti, e.getMessage());
        }
    }

    /** Redis 异常时 fail-open（视为未拉黑） */
    @Override
    public boolean isDenied(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            Boolean has = redis.hasKey(CacheKeys.denyJtiKey(jti));
            return Boolean.TRUE.equals(has);
        } catch (Exception e) {
            log.warn("isDenied Redis fail jti={}, fail-open: {}", jti, e.getMessage());
            return false;
        }
    }

    @Override
    public long getRefreshExpireSeconds() {
        return refreshExpireSeconds;
    }

    private Optional<RefreshSession> getFromRedis(String sid) {
        try {
            String json = redis.opsForValue().get(CacheKeys.refreshTokenKey(sid));
            if (json == null || json.isEmpty()) {
                return Optional.empty();
            }
            RefreshSession session = JSON.parseObject(json, RefreshSession.class);
            return Optional.ofNullable(session);
        } catch (Exception e) {
            log.warn("getRefresh Redis fail sid={}: {}", sid, e.getMessage());
            return Optional.empty();
        }
    }

    private void cacheRefresh(String sid, RefreshSession session) {
        try {
            redis.opsForValue().set(CacheKeys.refreshTokenKey(sid), JSON.toJSONString(session),
                    refreshExpireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("cacheRefresh Redis fail sid={}: {}", sid, e.getMessage());
        }
    }

    private static RefreshSession toRefreshSession(AuthRefreshSession row) {
        RefreshSession session = new RefreshSession();
        session.setUserId(row.getUserId());
        session.setRole(row.getRole());
        session.setRefreshJti(row.getRefreshJti());
        return session;
    }
}
