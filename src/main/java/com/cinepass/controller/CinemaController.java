package com.cinepass.controller;

import com.cinepass.aop.RateLimit;
import com.cinepass.common.Result;
import com.cinepass.dto.CinemaCreateDTO;
import com.cinepass.dto.CinemaUpdateDTO;
import com.cinepass.security.Admin;
import com.cinepass.security.Staff;
import com.cinepass.service.CinemaService;
import com.cinepass.service.ShowService;
import com.cinepass.vo.CinemaVO;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import com.cinepass.security.Public;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * 影院查询与后台管理。
 * <pre>
 * GET    /api/v1/cinemas              附近影院
 * GET    /api/v1/cinemas/{id}         详情（含影厅）
 * GET    /api/v1/cinemas/{id}/movies  当前时刻后在售影片（院→片）
 * POST   /api/v1/admin/cinemas        新建
 * PUT    /api/v1/admin/cinemas/{id}   更新
 * DELETE /api/v1/admin/cinemas/{id}   软删
 * </pre>
 */
@Api(tags = "影院管理")
@Validated
@RestController
@RequestMapping("/api/v1")
public class CinemaController {

    private final CinemaService cinemaService;
    private final ShowService showService;

    public CinemaController(CinemaService cinemaService, ShowService showService) {
        this.cinemaService = cinemaService;
        this.showService = showService;
    }

    /** 影院搜索：ES 全文检索（q）+ 按距离/价格排序 + 按影片筛选 */
    @ApiOperation("搜索影院")
    @GetMapping("/cinemas")
    @Public
    public Result<PageResult<CinemaVO>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String movieId,
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
            @RequestParam(required = false) Integer radiusMeters,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        return Result.success(cinemaService.listCinemas(q, movieId, lat, lng, radiusMeters, sort, page, size));
    }

    /** 影院详情，含下属影厅列表 */
    @ApiOperation("查询影院详情")
    @GetMapping("/cinemas/{cinemaId}")
    @Public
    public Result<CinemaVO> get(@PathVariable String cinemaId) {
        return Result.success(cinemaService.getCinema(cinemaId));
    }

    /** 院→片：当前时刻之后该院仍有 on_sale 场次的影片（含 nextShowDate） */
    @ApiOperation("查询影院在售影片")
    @GetMapping("/cinemas/{cinemaId}/movies")
    @Public
    public Result<PageResult<MovieVO>> listOnSaleMovies(@PathVariable String cinemaId) {
        return Result.success(showService.listOnSaleMovies(cinemaId));
    }

    /** 新建影院（仅 admin） */
    @ApiOperation("新建影院")
    @RateLimit(key = "admin-cinema", permits = 15, windowSeconds = 60)
    @PostMapping("/admin/cinemas")
    @Admin
    public Result<CinemaVO> create(@Valid @RequestBody CinemaCreateDTO body) {
        return Result.success(cinemaService.createCinema(body));
    }

    /** 更新影院；staff 仅能改本影院 */
    @ApiOperation("更新影院")
    @RateLimit(key = "admin-cinema", permits = 15, windowSeconds = 60)
    @PutMapping("/admin/cinemas/{cinemaId}")
    @Staff
    public Result<CinemaVO> update(@PathVariable String cinemaId, @Valid @RequestBody CinemaUpdateDTO body) {
        return Result.success(cinemaService.updateCinema(cinemaId, body));
    }

    /** 软删除影院；仍绑定员工时拒绝 */
    @ApiOperation("删除影院")
    @RateLimit(key = "admin-cinema", permits = 10, windowSeconds = 60)
    @DeleteMapping("/admin/cinemas/{cinemaId}")
    @Admin
    public Result<Void> delete(@PathVariable String cinemaId) {
        cinemaService.deleteCinema(cinemaId);
        return Result.success();
    }
}
