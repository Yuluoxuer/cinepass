package com.cinepass.controller;

import com.cinepass.cache.MovieChangedEvent;
import com.cinepass.common.Result;
import com.cinepass.dto.MovieCreateDTO;
import com.cinepass.dto.MovieUpdateDTO;
import com.cinepass.security.Staff;
import com.cinepass.service.MovieService;
import com.cinepass.vo.MovieVO;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public AdminMovieController(MovieService movieService,
                                ApplicationEventPublisher eventPublisher) {
        this.movieService = movieService;
        this.eventPublisher = eventPublisher;
    }

    /** 新建影片，创建后失效推荐底座缓存 */
    @PostMapping
    public Result<MovieVO> create(@Valid @RequestBody MovieCreateDTO body) {
        MovieVO vo = movieService.create(body);
        eventPublisher.publishEvent(new MovieChangedEvent(vo.getMovieId()));
        return Result.success(vo);
    }

    /** 部分更新影片，变更后失效推荐底座缓存 */
    @PutMapping("/{movieId}")
    public Result<MovieVO> update(@PathVariable String movieId,
                                  @Valid @RequestBody MovieUpdateDTO body) {
        MovieVO vo = movieService.update(movieId, body);
        eventPublisher.publishEvent(new MovieChangedEvent(movieId));
        return Result.success(vo);
    }

    /** 下架：校验该影片未来无在售场次；有则返回冲突提示 */
    @PostMapping("/{movieId}/take-down")
    public Result<MovieVO> takeDown(@PathVariable String movieId) {
        MovieVO vo = movieService.takeDown(movieId);
        eventPublisher.publishEvent(new MovieChangedEvent(movieId));
        return Result.success(vo);
    }

    /** 上架：按上映日期自动推导为热映或待映 */
    @PostMapping("/{movieId}/relist")
    public Result<MovieVO> relist(@PathVariable String movieId) {
        MovieVO vo = movieService.relist(movieId);
        eventPublisher.publishEvent(new MovieChangedEvent(movieId));
        return Result.success(vo);
    }
}
