package com.cinepass.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新场次入参（部分字段；null 表示不改）。
 */
@Data
public class ShowUpdateDTO {

    /** 开场时间 ISO-8601 */
    private String startTime;

    /** 散场时间 ISO-8601 */
    private String endTime;

    /** 统一价兜底 */
    private BigDecimal price;

    /** 分区价；非 null 时整体替换 */
    private List<ShowCreateDTO.ZonePriceItem> zonePrices;
}
