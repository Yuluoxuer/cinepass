package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class SeatMapSeatDTO {
    @NotNull
    @Min(1)
    private Integer graphRow;

    @NotNull
    @Min(1)
    private Integer graphCol;

    @Min(1)
    private Integer rowNo;

    @Min(1)
    private Integer colNo;

    @Size(max = 64)
    private String seatName;

    @Size(max = 64)
    private String seatId;

    @Size(max = 16)
    private String type;

    @Size(max = 32)
    private String zone;

    @Size(max = 64)
    private String couplePairId;

    @Size(max = 16)
    private String defaultStatus;
}
