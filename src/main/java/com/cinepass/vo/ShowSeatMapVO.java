package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 场次座位图（稀疏 seats + 包围盒 + 图例）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowSeatMapVO {

    /** 场次 ID */
    private String showId;

    /** 座位图 ID */
    private String seatMapId;

    /** 包围盒行数 */
    private Integer rows;

    /** 包围盒列数 */
    private Integer cols;

    /** 银幕文案 */
    private String screenLabel;

    /** 本场基础票价（元） */
    private BigDecimal price;

    /** 状态图例文案 */
    private Map<String, String> legend;

    /** 稀疏座位列表（仅有座格子） */
    private List<SeatVO> seats;
}
