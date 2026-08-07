package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 热门公式权重表 {@code reco_weight} 映射（系分 §3.6）。
 * <p>每城一行；四权重之和通常为 1.0。缺省 seed 城市 {@code city_sh}。</p>
 */
@Data
public class RecoWeight implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 城市 ID，如 {@code city_sh}；与 {@code cinema.city_id} 对齐 */
    private String cityId;

    /** 购票量权重 */
    private BigDecimal wOrders;

    /** 点击量权重 */
    private BigDecimal wClicks;

    /** 评分权重 */
    private BigDecimal wRating;

    /** 新鲜度权重 */
    private BigDecimal wFresh;

    /** 权重最后调整时间 */
    private OffsetDateTime updatedAt;
}
