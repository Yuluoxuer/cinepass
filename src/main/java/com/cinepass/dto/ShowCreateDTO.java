package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

/**
 * 新建场次入参。
 */
@Data
public class ShowCreateDTO {

    @NotBlank
    private String movieId;

    @NotBlank
    private String cinemaId;

    @NotBlank
    private String hallId;

    /** 开场时间 ISO-8601（含 offset） */
    @NotBlank
    private String startTime;

    /** 散场时间 ISO-8601（含 offset） */
    @NotBlank
    private String endTime;

    /** 分区价；优先于 {@link #price} */
    private List<ZonePriceItem> zonePrices;

    /** @deprecated 统一价兜底；新接口请用 zonePrices */
    @Deprecated
    private BigDecimal price;

    /** 分区价条目 */
    @Data
    public static class ZonePriceItem {
        private String zone;
        private BigDecimal price;
    }
}
