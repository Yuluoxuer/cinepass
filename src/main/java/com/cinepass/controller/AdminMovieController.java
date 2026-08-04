package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.MovieCreateDTO;
import com.cinepass.dto.MovieUpdateDTO;
import com.cinepass.security.Staff;
import com.cinepass.service.MovieService;
import com.cinepass.vo.MovieVO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 运营端电影管理。
 * <pre>
 * POST /api/v1/admin/movies
 * PUT  /api/v1/admin/movies/{movieId}
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/movies")
@Staff
public class AdminMovieController {

    private final MovieService movieService;

    public AdminMovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    /** 新建影片 */
    @PostMapping
    public Result<MovieVO> create(@Valid @RequestBody MovieCreateDTO body) {
        return Result.success(movieService.create(body));
    }

    /** 部分更新影片 */
    @PutMapping("/{movieId}")
    public Result<MovieVO> update(@PathVariable String movieId,
                                  @Valid @RequestBody MovieUpdateDTO body) {
        return Result.success(movieService.update(movieId, body));
    }
}
