package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.service.SeatInventoryService;
import com.cinepass.service.ShowService;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.ShowDetailVO;
import com.cinepass.vo.ShowListResult;
import com.cinepass.vo.ShowSeatMapVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * C 端场次查询（公开）。
 * <pre>
 * GET /api/v1/shows?cinemaId&movieId&date=
 * GET /api/v1/shows/{showId}
 * GET /api/v1/shows/{showId}/seat-map
 * GET /api/v1/shows/movies?startTime&endTime&cinemaId&page&size
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/shows")
public class ShowController {

    private final ShowService showService;
    private final SeatInventoryService seatInventoryService;

    public ShowController(ShowService showService, SeatInventoryService seatInventoryService) {
        this.showService = showService;
        this.seatInventoryService = seatInventoryService;
    }

    /** 指定影院+影片+日期的可售场次列表 */
    @GetMapping
    public Result<ShowListResult> list(@RequestParam String cinemaId,
                                        @RequestParam String movieId,
                                        @RequestParam String date) {
        return Result.success(showService.list(cinemaId, movieId, date));
    }

    /** 场次详情（含影片与影院摘要） */
    @GetMapping("/{showId}")
    public Result<ShowDetailVO> get(@PathVariable String showId) {
        return Result.success(showService.get(showId));
    }

    /** 场次座位图（稀疏 seats + 本场 status；空库存懒播种） */
    @GetMapping("/{showId}/seat-map")
    public Result<ShowSeatMapVO> seatMap(@PathVariable String showId) {
        return Result.success(seatInventoryService.getShowSeatMap(showId));
    }

    /**
     * 按时间段搜索有场次的电影（分页）。
     * startTime / endTime 为 ISO-8601（如 2026-08-10T13:00:00+08:00）；
     * cinemaId 为空时不限制影院。
     */
    @GetMapping("/movies")
    public Result<PageResult<MovieVO>> listMoviesByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) String cinemaId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        OffsetDateTime start = OffsetDateTime.parse(startTime);
        OffsetDateTime end = OffsetDateTime.parse(endTime);
        return Result.success(showService.listMoviesByTimeRange(start, end, cinemaId, page, size));
    }
}
