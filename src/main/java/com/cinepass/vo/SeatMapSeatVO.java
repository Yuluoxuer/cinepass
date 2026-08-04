package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeatMapSeatVO {
    private String seatId;
    private String seatName;
    private Integer rowNo;
    private Integer colNo;
    private Integer graphRow;
    private Integer graphCol;
    private String type;
    private String zone;
    private String defaultStatus;
    private String couplePairId;
}
