package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单座位价区快照（来自 order_ticket.seat_price_snapshot）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatPriceSnapshotVO {

    /** 系统座位 ID */
    private String seatId;

    /** 价区编码 */
    private String zone;

    /** 该座单价快照 */
    private BigDecimal price;

    /** 对号文案，如 6排7座 */
    private String seatName;
}
