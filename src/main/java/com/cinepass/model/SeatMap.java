package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeatMap implements Serializable {
    private static final long serialVersionUID = 1L;

    private String seatMapId;
    private String cinemaId;
    private Integer rowsN;
    private Integer colsN;
    private String screenLabel;
    private Boolean mutable;
    private Integer seatCount;
}
