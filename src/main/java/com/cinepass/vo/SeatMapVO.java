package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SeatMapVO {
    private String seatMapId;
    private String cinemaId;
    private Integer rows;
    private Integer cols;
    private String screenLabel;
    private Boolean mutable;
    private Integer seatCount;
    private List<SeatMapSeatVO> seats;
}
