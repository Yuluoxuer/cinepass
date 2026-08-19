package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 智能选座结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatRecoResultVO {

    /** 场次 ID */
    private String showId;

    /** 推荐方案，按 score 降序，最多 3 条；无解为 [] */
    private List<SeatPlanVO> plans;

    /**
     * 无理想解时的折中建议；有解时为 null。
     * 结构：{@code suggestion}、{@code altShowIds}
     */
    private Map<String, Object> compromise;
}
