package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ShowCreateDTO {
    @NotBlank
    private String movieId;

    @NotBlank
    private String cinemaId;

    @NotBlank
    private String hallId;

    @NotBlank
    private String startTime;

    @NotBlank
    private String endTime;

    private List<ZonePriceItem> zonePrices;

    @Deprecated
    private BigDecimal price;

    @Data
    public static class ZonePriceItem {
        private String zone;
        private BigDecimal price;
    }
}
