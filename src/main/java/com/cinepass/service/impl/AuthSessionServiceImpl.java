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
 * {@link AuthSessionService} 实现（Redis）。
 * <p>Refresh 会话按 sid 存；登出/吊销把 Access jti 写入 deny 列表直至原 TTL 到期。
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

    /** 登录成功后写入 Refresh 会话（key=sid，TTL=refreshExpireSeconds） */
    @Override
    public void saveRefresh(String sid, String userId, String role) {
        RefreshSession session = new RefreshSession();
        session.setUserId(userId);
        session.setRole(role);
        // refreshJti 预留轮换；静默续期时用 sid 找回会话
        session.setRefreshJti(UUID.randomUUID().toString().replace("-", ""));
        redis.opsForValue().set(CacheKeys.refreshTokenKey(sid), JSON.toJSONString(session),
                refreshExpireSeconds, TimeUnit.SECONDS);
    }

    /** 按 sid 读取 Refresh 会话；不存在或已过期返回 empty */
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

    /** 删除 Refresh 会话（登出） */
    @Override
    public void deleteRefresh(String sid) {
        if (sid != null) {
            redis.delete(CacheKeys.refreshTokenKey(sid));
        }
    }

    /** 将 Access Token 的 jti 加入黑名单，TTL 覆盖剩余有效期 */
    @Override
    public void denyJti(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) {
            return;
        }
        // TTL 与 Access 剩余寿命对齐，过期后自然失效，无需扫表
        redis.opsForValue().set(CacheKeys.denyJtiKey(jti), "1", ttlSeconds, TimeUnit.SECONDS);
    }

    /** 判断 jti 是否已拉黑（登出后的 Access 拒绝） */
    @Override
    public boolean isDenied(String jti) {
        if (jti == null) {
            return false;
        }
        Boolean has = redis.hasKey(CacheKeys.denyJtiKey(jti));
        return Boolean.TRUE.equals(has);
    }

    /** 返回 Refresh Token 配置的过期秒数 */
    @Override
    public long getRefreshExpireSeconds() {
        return refreshExpireSeconds;
    }
}
