package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 场次详情展示对象（含影片与影院摘要）。
 */
@Data
@Builder
public class ShowDetailVO {

    private String showId;
    private String movieId;
    private String cinemaId;
    private String hallId;
    private String hallName;

    /** 开场时间 ISO-8601 */
    private String startTime;

    /** 散场时间 ISO-8601 */
    private String endTime;

    private BigDecimal price;
    private int seatRemain;
    private String seatRemainLevel;

    /** 影片摘要 */
    private MovieVO movie;

    /** 影院摘要 */
    private CinemaBrief cinema;

    /** 详情内嵌的影院简要信息 */
    @Data
    @Builder
    public static class CinemaBrief {
        private String cinemaId;
        private String name;
        private String address;
    }
}
