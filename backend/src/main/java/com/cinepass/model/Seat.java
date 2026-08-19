package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 座位表 {@code seat} 映射（隶属某座位图）。
 */
@Data
public class Seat implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 座位业务 ID */
    private String seatId;

    /** 所属座位图 ID */
    private String seatMapId;

    /** 画布行坐标（1-based） */
    private Integer graphRow;

    /** 画布列坐标（1-based） */
    private Integer graphCol;

    /** 业务排号 */
    private Integer rowNo;

    /** 业务座号 */
    private Integer colNo;

    /** 展示名 */
    private String seatName;

    /** 类型：normal / couple / disabled */
    private String seatType;

    /** 分区 */
    private String zone;

    /** 情侣座配对 ID */
    private String couplePairId;

    /** 默认售卖状态：available / unavailable */
    private String defaultStatus;
}
