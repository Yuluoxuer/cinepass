package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 座位图对外展示对象。
 */
@Data
@Builder
public class SeatMapVO {

    /** 座位图 ID */
    private String seatMapId;

    /** 所属影院 ID */
    private String cinemaId;

    /** 画布行数 */
    private Integer rows;

    /** 画布列数 */
    private Integer cols;

    /** 银幕文案 */
    private String screenLabel;

    /** 是否仍可编辑；绑定影厅排片后通常不可变 */
    private Boolean mutable;

    /** 有效座位数 */
    private Integer seatCount;

    /** 座位明细 */
    private List<SeatMapSeatVO> seats;
}
