package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 场次分区价表 {@code show_zone_price} 映射。
 */
@Data
public class ShowZonePrice implements Serializable {

    private static final long serialVersionUID = 1L;

    private String showId;

    /** 分区 code，与座位图 seat.zone 对齐（含自定义区名） */
    private String zone;

    private BigDecimal price;
}
