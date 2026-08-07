package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 影片点击日计数表 {@code reco_clicks} 映射。
 * <p>影片详情每次访问 UPSERT 当日 cnt+1；{@code reco_stats.week_clicks} = 近 7 天 SUM(cnt)。</p>
 */
@Data
public class RecoClick implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 影片 ID */
    private String movieId;

    /** 点击日期（本地日，Asia/Shanghai） */
    private LocalDate clickDate;

    /** 当日累计点击数 */
    private Integer cnt;
}
