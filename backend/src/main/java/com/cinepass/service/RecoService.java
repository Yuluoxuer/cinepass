package com.cinepass.service;

import com.cinepass.vo.PersonalRecoVO;
import com.cinepass.vo.WeeklyHotVO;

/**
 * 推荐服务：每周热门 + 个人推荐 + 周信号物化重算。
 * <p>纯算法打分，不依赖 LLM（系分 B-G5）；周信号物化到 {@code reco_stats}，热门分可按城市权重即时重算。</p>
 */
public interface RecoService {

    /**
     * 每周热门榜单；未登录也可调用。
     * <p>reco_stats 为空或过期时同步重算兜底；按城市权重对原始信号重新打分排序。</p>
     *
     * @param cityId 城市 ID，空则默认 {@code city_sh}
     * @param limit  返回条数，默认 10，范围 1–20
     */
    WeeklyHotVO weeklyHot(String cityId, Integer limit);

    /**
     * 个人推荐；可选登录。
     * <p>未登录回退热门榜（mode=fallback_hot）；已登录按
     * {@code 0.5*类型匹配 + 0.2*行为匹配 + 0.2*评分 + 0.1*热度} 打分，同类型连续 ≤3。</p>
     *
     * @param userId          登录用户 ID，null 视为未登录
     * @param limit           返回条数，默认 10，最大 20
     * @param excludeMovieIds 逗号分隔要排除的影片 ID，可为空
     */
    PersonalRecoVO personal(String userId, Integer limit, String excludeMovieIds);

    /** 全量重算 reco_stats 周信号物化（定时任务与榜单过期兜底共用）；无数据时安全跳过 */
    void recomputeStats();
}
