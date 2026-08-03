package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CinemaVO {
    private String cinemaId;
    private String cityId;
    private String name;
    private String address;
    private BigDecimal distanceMeters;
    private BigDecimal minPrice;
    private String trafficNote;
    private List<String> tags;
    private List<HallVO> halls;
}
