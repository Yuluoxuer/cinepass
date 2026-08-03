package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class Seat implements Serializable {
    private static final long serialVersionUID = 1L;

    private String seatId;
    private String seatMapId;
    private Integer graphRow;
    private Integer graphCol;
    private Integer rowNo;
    private Integer colNo;
    private String seatName;
    private String seatType;
    private String zone;
    private String couplePairId;
    private String defaultStatus;
}
