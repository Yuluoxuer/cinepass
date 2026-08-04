package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ShowVO {
    private String showId;
    private String movieId;
    private String cinemaId;
    private String hallId;
    private String hallName;
    private String startTime;
    private String endTime;
    private BigDecimal price;
    private List<ZonePriceVO> zonePrices;
    private int seatRemain;
    private String seatRemainLevel;
    private String status;

    @Data
    @Builder
    public static class ZonePriceVO {
        private String zone;
        private BigDecimal price;
    }
}
