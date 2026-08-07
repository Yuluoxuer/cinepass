package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 每周热门榜单响应（系分 §4.1）。
 */
@Data
@Builder
public class WeeklyHotVO {

    /** 热门分计算时间（ISO-8601） */
    private String computedAt;

    /** 榜单项，按 hotScore 降序 */
    private List<WeeklyHotItemVO> items;
}
