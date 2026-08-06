package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 座位图表 {@code seat_map} 映射。
 */
@Data
public class SeatMap implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 座位图 ID */
    private String seatMapId;

    /** 座位图名称（运营展示用） */
    private String name;

    /** 所属影院 ID */
    private String cinemaId;

    /** 画布行数 */
    private Integer rowsN;

    /** 画布列数 */
    private Integer colsN;

    /** 银幕文案 */
    private String screenLabel;

    /** 是否可编辑 */
    private Boolean mutable;

    /** 有效座位数 */
    private Integer seatCount;
}
