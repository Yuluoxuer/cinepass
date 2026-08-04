package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.SeatMapCreateDTO;
import com.cinepass.security.Staff;
import com.cinepass.service.CinemaService;
import com.cinepass.vo.SeatMapVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 座位图运营接口。
 * <pre>
 * POST /api/v1/seat-maps  创建稀疏座位图
 * </pre>
 */
@Api(tags = "座位图运营")
@RestController
@RequestMapping("/api/v1/seat-maps")
@Staff
public class SeatMapController {

    private final CinemaService cinemaService;

    public SeatMapController(CinemaService cinemaService) {
        this.cinemaService = cinemaService;
    }

    /** 创建稀疏座位图并批量写入座位行 */
    @ApiOperation("创建稀疏座位图")
    @PostMapping
    public Result<SeatMapVO> create(@Valid @RequestBody SeatMapCreateDTO body) {
        return Result.success(cinemaService.createSeatMap(body));
    }
}
