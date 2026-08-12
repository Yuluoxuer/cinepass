package com.cinepass.controller;

import com.cinepass.aop.RateLimit;
import com.cinepass.common.Result;
import com.cinepass.dto.HallCreateDTO;
import com.cinepass.dto.HallUpdateDTO;
import com.cinepass.security.Staff;
import com.cinepass.service.CinemaService;
import com.cinepass.vo.HallVO;
import com.cinepass.vo.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
import javax.validation.constraints.Min;

/**
 * 影厅运营接口（新建 / 列表 / 改名）。
 * <pre>
 * POST /api/v1/halls
 * GET  /api/v1/admin/halls
 * PUT  /api/v1/admin/halls/{hallId}
 * </pre>
 */
@Api(tags = "影厅运营")
@RestController
@RequestMapping("/api/v1")
@Staff
@Validated
public class HallController {

    private final CinemaService cinemaService;

    public HallController(CinemaService cinemaService) {
        this.cinemaService = cinemaService;
    }

    /** 新建影厅并绑定已有座位图 */
    @ApiOperation("新建影厅并绑定座位图")
    @RateLimit(key = "admin-hall", permits = 15, windowSeconds = 60)
    @PostMapping("/halls")
    public Result<HallVO> create(@Valid @RequestBody HallCreateDTO body) {
        return Result.success(cinemaService.createHall(body));
    }

    /** 运营端按影院分页影厅；admin 须传 cinemaId */
    @ApiOperation("运营端影厅列表")
    @GetMapping("/admin/halls")
    public Result<PageResult<HallVO>> list(@RequestParam(required = false) String cinemaId,
                                            @RequestParam(defaultValue = "1") @Min(1) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return Result.success(cinemaService.listAdminHalls(cinemaId, page, size));
    }

    /** 仅修改影厅名称 */
    @ApiOperation("修改影厅名称")
    @RateLimit(key = "admin-hall", permits = 15, windowSeconds = 60)
    @PutMapping("/admin/halls/{hallId}")
    public Result<HallVO> update(@PathVariable String hallId, @Valid @RequestBody HallUpdateDTO body) {
        return Result.success(cinemaService.updateHall(hallId, body));
    }
}
