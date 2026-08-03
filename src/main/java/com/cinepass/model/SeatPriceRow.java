package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 座位价区查询行。
 */
@Data
public class SeatPriceRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 座位 ID */
    private String seatId;

    /** 价区编码 */
    private String zone;

    /** 展示用座位名，如 5排8座 */
    private String seatName;

    /** 该座位单价；可空则回退场次 basePrice */
    private BigDecimal price;
}
