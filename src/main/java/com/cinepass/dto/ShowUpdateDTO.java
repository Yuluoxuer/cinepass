package com.cinepass.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ShowUpdateDTO {
    private String startTime;
    private String endTime;
    private BigDecimal price;
    private List<ShowCreateDTO.ZonePriceItem> zonePrices;
}
