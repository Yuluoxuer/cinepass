package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场次座位图中单个座位（含本场占用状态）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatVO {

    /** 系统座位键；锁座/下单用 */
    private String seatId;

    /** 对号文案，如「6排7座」 */
    private String seatName;

    /** 业务排号 */
    private Integer rowNo;

    /** 业务座号 */
    private Integer colNo;

    /** 画布行（1-based） */
    private Integer graphRow;

    /** 画布列（1-based） */
    private Integer graphCol;

    /** 类型：normal / couple / disabled */
    private String type;

    /** 分区：normal / golden 等 */
    private String zone;

    /** 本场状态：available / locked / sold / unavailable */
    private String status;

    /** 情侣座配对 ID；非情侣座为 null */
    private String couplePairId;
}
