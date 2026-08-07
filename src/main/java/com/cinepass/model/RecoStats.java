package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 热门推荐物化统计表 {@code reco_stats} 映射（系分 §3.6）。
 * <p>每片一行；由定时任务按周信号重算后全量重写，GET /reco/weekly-hot 取 Top-N。</p>
 */
@Data
public class RecoStats implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 影片 ID */
    private String movieId;

    /** 近 7 天购票订单数（原始计数，计算时归一化） */
    private Integer weekOrders;

    /** 近 7 天影片详情点击数（来自 {@code reco_clicks}） */
    private Integer weekClicks;

    /** 评分归一化到 0–1（评分/10） */
    private BigDecimal ratingNorm;

    /** 新鲜度 0–1；上映越近越高，待映按距上映天数 */
    private BigDecimal freshness;

    /** 物化热门分（0–100，按默认城市权重加权） */
    private BigDecimal hotScore;

    /** 本次重算时间 */
    private OffsetDateTime computedAt;
}
