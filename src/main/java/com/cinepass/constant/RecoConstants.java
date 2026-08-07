package com.cinepass.constant;

import java.math.BigDecimal;

/**
 * 推荐模块常量（系分 §4.1 / §4.2 / §3.6）。
 */
public final class RecoConstants {

    private RecoConstants() {
    }

    /** 默认城市：无 cityId 参数或权重缺失时回退 */
    public static final String DEFAULT_CITY = "city_sh";

    /** 热门分四项默认权重（系分 §3.6 默认值，和为 1.0） */
    public static final BigDecimal W_ORDERS = new BigDecimal("0.45");
    public static final BigDecimal W_CLICKS = new BigDecimal("0.25");
    public static final BigDecimal W_RATING = new BigDecimal("0.15");
    public static final BigDecimal W_FRESH = new BigDecimal("0.15");

    /** 新鲜度衰减窗口（天）：已上映 90 天内热度递减，待映距上映 30 天内递增 */
    public static final int SHOWING_FRESH_WINDOW_DAYS = 90;
    public static final int COMING_SOON_FRESH_WINDOW_DAYS = 30;

    /** reco_stats 物化超过该时长（毫秒）视为过期，GET 时同步重算兜底 */
    public static final long STATS_STALE_MS = 3_600_000L;

    /** 每周热门 limit 上限 */
    public static final int WEEKLY_HOT_MAX = 20;

    /** 个人推荐 limit 上限 */
    public static final int PERSONAL_MAX = 20;

    /** 个人推荐出参 mode（系分 §4.2） */
    public static final String MODE_PERSONALIZED = "personalized";
    public static final String MODE_FALLBACK_HOT = "fallback_hot";

    /** 同类型连续输出上限（系分 §4.2） */
    public static final int MAX_SAME_GENRE_RUN = 3;
}
