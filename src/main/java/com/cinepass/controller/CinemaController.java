package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.CinemaCreateDTO;
import com.cinepass.dto.CinemaUpdateDTO;
import com.cinepass.security.Admin;
import com.cinepass.security.Staff;
import com.cinepass.service.CinemaService;
import com.cinepass.vo.CinemaVO;
import com.cinepass.vo.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
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

@Api(tags = "影院管理")
@Validated
@RestController
@RequestMapping("/api/v1")
public class CinemaController {
    private final CinemaService cinemaService;
    public CinemaController(CinemaService cinemaService) { this.cinemaService = cinemaService; }

    @ApiOperation("查询附近影院")
    @GetMapping("/cinemas")
    @PreAuthorize("permitAll()")
    public Result<PageResult<CinemaVO>> list(
            @RequestParam(required = false) String movieId,
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
            @RequestParam(required = false) Integer radiusMeters,
            @RequestParam(defaultValue = "distance") String sort,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return Result.success(cinemaService.listCinemas(movieId, lat, lng, radiusMeters, sort, page, size));
    }

    @ApiOperation("查询影院详情")
    @GetMapping("/cinemas/{cinemaId}")
    @PreAuthorize("permitAll()")
    public Result<CinemaVO> get(@PathVariable String cinemaId) {
        return Result.success(cinemaService.getCinema(cinemaId));
    }

    @ApiOperation("新建影院")
    @PostMapping("/admin/cinemas")
    @Admin
    public Result<CinemaVO> create(@Valid @RequestBody CinemaCreateDTO body) {
        return Result.success(cinemaService.createCinema(body));
    }

    @ApiOperation("更新影院")
    @PutMapping("/admin/cinemas/{cinemaId}")
    @Staff
    public Result<CinemaVO> update(@PathVariable String cinemaId, @Valid @RequestBody CinemaUpdateDTO body) {
        return Result.success(cinemaService.updateCinema(cinemaId, body));
    }

    @ApiOperation("删除影院")
    @DeleteMapping("/admin/cinemas/{cinemaId}")
    @Admin
    public Result<Void> delete(@PathVariable String cinemaId) {
        cinemaService.deleteCinema(cinemaId);
        return Result.success();
    }
}
