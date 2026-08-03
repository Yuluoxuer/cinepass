package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HallVO {
    private String hallId;
    private String cinemaId;
    private String name;
    private String seatMapId;
    private Integer showCount;
}
