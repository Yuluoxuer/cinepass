package com.cinepass.controller;

import com.cinepass.aop.RateLimit;
import com.cinepass.common.Result;
import com.cinepass.service.MovieService;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import com.cinepass.security.Public;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端电影查询（公开）。
 * <pre>
 * GET /api/v1/movies             搜索/筛选影片（ES 全文检索 + 状态过滤）
 * GET /api/v1/movies/{movieId}   影片详情
 * </pre>
 */
@Api(tags = "影片搜索")
@RestController
@RequestMapping("/api/v1/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    /**
     * 搜索影片：q 非空时走 ES multi_match(title^4/cast^2/description)；
     * 可按 status（hot_showing/coming_soon/off）和 genre 筛选。
     */
    @ApiOperation("搜索/筛选影片（q 走 ES 全文检索，可选 status/genre 过滤）")
    @RateLimit(key = "movies-list", permits = 120, windowSeconds = 60)
    @GetMapping
    @Public
    public Result<PageResult<MovieVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(movieService.page(status, q, genre, page, size));
    }

    /** 电影详情 */
    @ApiOperation("电影详情")
    @GetMapping("/{movieId}")
    @Public
    public Result<MovieVO> get(@PathVariable String movieId) {
        return Result.success(movieService.get(movieId));
    }

    /** 全部影片类型标签（来自 tag 字典表，按名称排序） */
    @ApiOperation("全部影片类型标签")
    @GetMapping("/genres")
    @Public
    public Result<List<String>> genres() {
        return Result.success(movieService.listGenres());
    }
}
