package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ShowDetailVO {
    private String showId;
    private String movieId;
    private String cinemaId;
    private String hallId;
    private String hallName;
    private String startTime;
    private String endTime;
    private BigDecimal price;
    private int seatRemain;
    private String seatRemainLevel;
    private MovieVO movie;
    private CinemaBrief cinema;

    @Data
    @Builder
    public static class CinemaBrief {
        private String cinemaId;
        private String name;
        private String address;
    }
}
