package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 场次列表项展示对象。
 */
@Data
@Builder
public class ShowVO {

    private String showId;
    private String movieId;
    private String cinemaId;
    private String hallId;
    private String hallName;

    /** 开场时间 ISO-8601 */
    private String startTime;

    /** 散场时间 ISO-8601 */
    private String endTime;

    /** 展示用最低/统一价 */
    private BigDecimal price;

    /** 分区价列表 */
    private List<ZonePriceVO> zonePrices;

    /** 余座数 */
    private int seatRemain;

    /** 余座紧张度，如 plenty / limited / scarce */
    private String seatRemainLevel;

    /** 场次状态，如 on_sale / closed / cancelled */
    private String status;

    /** 分区价展示项 */
    @Data
    @Builder
    public static class ZonePriceVO {
        private String zone;
        private BigDecimal price;
    }
}
