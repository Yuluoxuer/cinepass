package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 每周热门榜单单项（系分 §4.1）。
 */
@Data
@Builder
public class WeeklyHotItemVO {

    /** 榜单名次 1..N */
    private Integer rank;

    /** 热门分 0–100，保留 1 位小数 */
    private BigDecimal hotScore;

    /** 热度文案标签，如「本周爆款」 */
    private String heatTag;

    /** 影片快照 */
    private MovieVO movie;
}
