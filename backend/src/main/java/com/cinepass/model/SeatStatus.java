package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 场次座位状态表 {@code seat_status} 映射。
 */
@Data
public class SeatStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    private String showId;
    private String seatId;

    /** available / locked / sold 等 */
    private String status;

    /** 当前锁座凭证；未锁为 null */
    private String lockId;

    /** 占座用户；未占为 null */
    private String userId;

    /** 锁座过期时间 */
    private OffsetDateTime expireAt;

    private OffsetDateTime updatedAt;
}
