package com.cinepass.service;

import com.cinepass.model.ShowSchedule;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.ShowDetailVO;
import com.cinepass.vo.ShowListResult;
import com.cinepass.vo.ShowVO;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * C 端场次查询与 VO 组装。
 */
public interface ShowService {

    /** 指定影院+影片+日期的场次列表 */
    ShowListResult list(String cinemaId, String movieId, String date);

    /** 指定影院+影片的全部场次（运营列表无 date） */
    ShowListResult listAll(String cinemaId, String movieId);

    /**
     * 院→片：当前时刻之后该影院仍有 on_sale 场次的影片（含 nextShowDate）。
     * 影院不存在则 404。
     */
    PageResult<MovieVO> listOnSaleMovies(String cinemaId);

    /** 场次详情；附影片与影院摘要 */
    ShowDetailVO get(String showId);

    /** 由排片行组装 ShowVO（含余座与紧张度） */
    ShowVO buildShowVO(ShowSchedule s);

    /** 组装 ShowVO，并覆盖分区价展示 */
    ShowVO buildShowVO(ShowSchedule s, List<ShowVO.ZonePriceVO> zonePrices);

    /**
     * 按时间段搜索有 on_sale 场次的电影（分页）。
     * cinemaId 为空时不限制影院；按最早开场时间升序。
     */
    PageResult<MovieVO> listMoviesByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime,
                                              String cinemaId, int page, int size);
}
