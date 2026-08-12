package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Refresh 会话表 {@code auth_refresh_session} 映射（DB 真相；Redis 为缓存）。
 */
@Data
public class AuthRefreshSession implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 会话 ID（与 Access JWT claim sid 一致） */
    private String sid;

    /** 用户 ID */
    private String userId;

    /** 登录时角色快照 */
    private String role;

    /** Refresh 自身 jti（预留轮换） */
    private String refreshJti;

    /** 会话过期时间 */
    private OffsetDateTime expireAt;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
