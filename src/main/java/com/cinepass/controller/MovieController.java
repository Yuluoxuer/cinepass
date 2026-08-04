package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.service.MovieService;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public Result<PageResult<MovieVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(movieService.page(status, q, genre, page, size));
    }

    @GetMapping("/{movieId}")
    public Result<MovieVO> get(@PathVariable String movieId) {
        return Result.success(movieService.get(movieId));
    }
}
