package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 座位图中单个座位入参（稀疏布局）。
 */
@Data
public class SeatMapSeatDTO {

    /** 画布行（1-based，必填） */
    @NotNull
    @Min(1)
    private Integer graphRow;

    /** 画布列（1-based，必填） */
    @NotNull
    @Min(1)
    private Integer graphCol;

    /** 业务排号；空则按非空画布行自动编号 */
    @Min(1)
    private Integer rowNo;

    /** 业务座号；空则按同行从左到右编号 */
    @Min(1)
    private Integer colNo;

    @Size(max = 64)
    private String seatName;

    /** 可选；空则 {@code seatMapId:graphRow:graphCol} */
    @Size(max = 64)
    private String seatId;

    /** normal / couple / disabled；默认 normal */
    @Size(max = 16)
    private String type;

    /** 分区；默认 normal */
    @Size(max = 32)
    private String zone;

    /** 情侣座配对；type=couple 时必填 */
    @Size(max = 64)
    private String couplePairId;

    /** available / unavailable；默认 available */
    @Size(max = 16)
    private String defaultStatus;
}
