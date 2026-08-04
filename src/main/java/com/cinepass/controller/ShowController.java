package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.service.ShowService;
import com.cinepass.vo.ShowDetailVO;
import com.cinepass.vo.ShowListResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @GetMapping
    public Result<ShowListResult> list(@RequestParam String cinemaId,
                                        @RequestParam String movieId,
                                        @RequestParam String date) {
        return Result.success(showService.list(cinemaId, movieId, date));
    }

    @GetMapping("/{showId}")
    public Result<ShowDetailVO> get(@PathVariable String showId) {
        return Result.success(showService.get(showId));
    }
}
