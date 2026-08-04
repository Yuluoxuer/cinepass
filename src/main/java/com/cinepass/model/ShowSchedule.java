package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class ShowSchedule implements Serializable {
    private static final long serialVersionUID = 1L;
    private String showId;
    private String movieId;
    private String cinemaId;
    private String hallId;
    private String seatMapId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private BigDecimal price;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** 非DB字段，JOIN hall获取 */
    private String hallName;
    /** 非DB字段，seat_status聚合 */
    private int seatRemain;
    /** 非DB字段，seat_status聚合 */
    private int totalSeats;
}
