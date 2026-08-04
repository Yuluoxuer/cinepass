package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.SeatMapCreateDTO;
import com.cinepass.dto.SeatMapUpdateDTO;
import com.cinepass.security.Staff;
import com.cinepass.service.CinemaService;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.SeatMapDeletedVO;
import com.cinepass.vo.SeatMapVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Min;

/**
 * 座位图运营接口。
 * <pre>
 * POST   /api/v1/seat-maps              创建
 * GET    /api/v1/seat-maps              列表（staff 本院；admin 可筛 cinemaId）
 * GET    /api/v1/seat-maps/{seatMapId}  详情
 * PUT    /api/v1/seat-maps/{seatMapId}  更新（mutable）
 * DELETE /api/v1/seat-maps/{seatMapId}  删除（无厅/场次引用）
 * </pre>
 */
@Api(tags = "座位图运营")
@RestController
@RequestMapping("/api/v1/seat-maps")
@Staff
@Validated
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

    /** 运营端座位图分页；staff 强制本院，admin 可按 cinemaId 筛选 */
    @ApiOperation("座位图列表")
    @GetMapping
    public Result<PageResult<SeatMapVO>> list(@RequestParam(required = false) String cinemaId,
                                              @RequestParam(defaultValue = "1") @Min(1) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) int size) {
        return Result.success(cinemaService.listSeatMaps(cinemaId, page, size));
    }

    /** 查询座位图模板详情（含座位明细） */
    @ApiOperation("座位图详情")
    @GetMapping("/{seatMapId}")
    public Result<SeatMapVO> get(@PathVariable String seatMapId) {
        return Result.success(cinemaService.getSeatMap(seatMapId));
    }

    /** 全量替换座位集合；仅 mutable 时可改 */
    @ApiOperation("更新座位图")
    @PutMapping("/{seatMapId}")
    public Result<SeatMapVO> update(@PathVariable String seatMapId,
                                    @Valid @RequestBody SeatMapUpdateDTO body) {
        return Result.success(cinemaService.updateSeatMap(seatMapId, body));
    }

    /** 删除座位图；仍被影厅或场次引用时 409 */
    @ApiOperation("删除座位图")
    @DeleteMapping("/{seatMapId}")
    public Result<SeatMapDeletedVO> delete(@PathVariable String seatMapId) {
        return Result.success(cinemaService.deleteSeatMap(seatMapId));
    }
}
