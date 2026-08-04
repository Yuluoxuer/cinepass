package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class Cinema implements Serializable {
    private static final long serialVersionUID = 1L;

    private String cinemaId;
    private String cityId;
    private String cityName;
    private String name;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private String trafficNote;
    private String tagsJson;
    private BigDecimal distanceMeters;
    private BigDecimal minPrice;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
