package com.cinepass.service;

import lombok.Data;

import java.util.Optional;

/**
 * 登录会话管理：Refresh 会话落 Redis、Access jti 黑名单（登出/吊销）。
 */
public interface AuthSessionService {

    /** 保存 Refresh 会话（含 userId、role），TTL 由配置决定 */
    void saveRefresh(String sid, String userId, String role);

    /** 按 sid 读取 Refresh 会话；不存在返回 empty */
    Optional<RefreshSession> getRefresh(String sid);

    /** 删除 Refresh 会话（登出） */
    void deleteRefresh(String sid);

    /** 将 Access Token 的 jti 加入黑名单，TTL 覆盖剩余有效期 */
    void denyJti(String jti, long ttlSeconds);

    /** 判断 jti 是否已被吊销 */
    boolean isDenied(String jti);

    /** 返回 Refresh 会话默认过期秒数 */
    long getRefreshExpireSeconds();

    /** Redis 中存储的 Refresh 会话结构 */
    @Data
    class RefreshSession {
        /** 用户 ID */
        private String userId;
        /** 登录时角色快照 */
        private String role;
        /** Refresh 自身的 jti */
        private String refreshJti;
    }
}
