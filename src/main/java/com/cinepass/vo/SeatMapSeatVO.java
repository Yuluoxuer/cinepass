package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 座位图中单个座位的展示对象。
 */
@Data
@Builder
public class SeatMapSeatVO {

    /** 座位业务 ID */
    private String seatId;

    /** 展示名，如「3排5座」 */
    private String seatName;

    /** 业务排号 */
    private Integer rowNo;

    /** 业务座号 */
    private Integer colNo;

    /** 画布行坐标（1-based） */
    private Integer graphRow;

    /** 画布列坐标（1-based） */
    private Integer graphCol;

    /** 类型：normal / couple / disabled */
    private String type;

    /** 分区，用于分区定价 */
    private String zone;

    /** 默认售卖状态：available / unavailable */
    private String defaultStatus;

    /** 情侣座配对 ID；非情侣座为 null */
    private String couplePairId;
}
