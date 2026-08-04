package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class Hall implements Serializable {
    private static final long serialVersionUID = 1L;

    private String hallId;
    private String cinemaId;
    private String name;
    private String seatMapId;
    private Integer showCount;
}
