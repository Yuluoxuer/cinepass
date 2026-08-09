package com.cinepass.vo;

import com.cinepass.model.RecoStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 推荐共享底座：候选影片 + 热度分信号（所有用户共享，Redis 缓存）。
 * <p>只含 hot_showing + coming_soon 影片；用户画像/想看/订单等个性化数据不在其中（每次现读）。
 * 注意：movies 中 {@link MovieVO#getWantSeeCount()} 会被物化缓存，想看增减不触发失效，
 * 该字段最多滞后一个逻辑 TTL（默认 10 分钟），且不影响推荐排序（排序只用类型/评分/热度分）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoBaseVO {

    /** reco_stats 计算时间（ISO-8601） */
    private String computedAt;

    /** movieId → 热度信号（weekOrders / weekClicks / ratingNorm / freshness / hotScore） */
    private Map<String, RecoStats> signals;

    /** movieId → 影片 VO */
    private Map<String, MovieVO> movies;
}
