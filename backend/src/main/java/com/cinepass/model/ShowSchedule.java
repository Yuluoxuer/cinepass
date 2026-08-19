package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 场次表 {@code show_schedule} 映射。
 */
@Data
public class ShowSchedule implements Serializable {
    private static final long serialVersionUID = 1L;

    private String showId;
    private String movieId;
    private String cinemaId;
    private String hallId;

    /** 开场时绑定的座位图快照 ID */
    private String seatMapId;

    private OffsetDateTime startTime;
    private OffsetDateTime endTime;

    /** 统一价或最低分区价 */
    private BigDecimal price;

    /** on_sale / closed / cancelled 等 */
    private String status;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** 非表字段：JOIN hall */
    private String hallName;

    /** 非表字段：JOIN movie */
    private String movieTitle;

    /** 非表字段：seat_status 余座聚合 */
    private int seatRemain;

    /** 非表字段：seat_status 总座聚合 */
    private int totalSeats;
}
