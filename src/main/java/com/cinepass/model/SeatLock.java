package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 锁座凭证表 {@code seat_lock} 映射。
 */
@Data
public class SeatLock implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 锁座凭证 ID */
    private String lockId;

    /** 场次 ID */
    private String showId;

    /** 锁座用户 ID */
    private String userId;

    /** 座位 ID 列表 JSON */
    private String seatIdsJson;

    /** 状态：active / expired / consumed / released */
    private String status;

    /** 锁座 TTL（秒） */
    private Integer ttlSeconds;

    /** 锁座过期时间 */
    private OffsetDateTime expireAt;

    /** Agent/会话 ID，可选 */
    private String sessionId;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
