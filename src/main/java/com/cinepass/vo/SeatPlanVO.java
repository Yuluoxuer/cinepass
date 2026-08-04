package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 智能选座方案。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatPlanVO {

    /** 方案 ID（会话内唯一） */
    private String planId;

    /** 推荐座位系统键 */
    private List<String> seatIds;

    /** 综合得分 0–100 */
    private Double score;

    /** 人可读解释文案 */
    private String explain;

    /** 可选展开的座位明细 */
    private List<SeatVO> seats;
}
