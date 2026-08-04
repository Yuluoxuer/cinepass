package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 锁座凭证对外 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockVO {

    /** 锁座凭证 ID（lk + UUID7） */
    private String lockId;

    /** 场次 ID */
    private String showId;

    /** 已锁系统 seatId */
    private List<String> seatIds;

    /** 锁归属用户 */
    private String userId;

    /** 锁到期时间（ISO-8601） */
    private String expireAt;

    /** 本次 TTL（秒） */
    private Integer ttlSeconds;

    /** 状态：active / expired / consumed / released */
    private String status;
}
