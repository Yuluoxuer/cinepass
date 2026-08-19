package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 购票草稿对外 VO（与前端 BookingDraft 对齐）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDraftVO {

    private String sessionId;
    private String userId;
    private String source;
    private String state;
    private String intent;
    private String movieId;
    private String filmTitle;
    private String genre;
    private String date;
    private String timeWindow;
    private BigDecimal lat;
    private BigDecimal lng;
    private String cinemaId;
    private String showId;
    private Integer count;
    @Builder.Default
    private List<String> seatIds = new ArrayList<String>();
    private String preferRow;
    private String preferSide;
    private Boolean together;
    private BigDecimal budgetMax;
    private Map<String, Object> listContext;
    private String lockId;
    private String orderId;
    private String expireAt;
    private Long version;
    private String updatedAt;
}
