package com.cinepass.controller;

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

import javax.validation.Valid;
import javax.validation.constraints.Min;

@Api(tags = "影厅运营")
@RestController
@RequestMapping("/api/v1")
@Staff
public class HallController {
    private final CinemaService cinemaService;
    public HallController(CinemaService cinemaService) { this.cinemaService = cinemaService; }

    @ApiOperation("新建影厅并绑定座位图")
    @PostMapping("/halls")
    public Result<HallVO> create(@Valid @RequestBody HallCreateDTO body) {
        return Result.success(cinemaService.createHall(body));
    }

    @ApiOperation("运营端影厅列表")
    @GetMapping("/admin/halls")
    public Result<PageResult<HallVO>> list(@RequestParam(required = false) String cinemaId,
                                            @RequestParam(defaultValue = "1") @Min(1) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return Result.success(cinemaService.listAdminHalls(cinemaId, page, size));
    }

    @ApiOperation("修改影厅名称")
    @PutMapping("/admin/halls/{hallId}")
    public Result<HallVO> update(@PathVariable String hallId, @Valid @RequestBody HallUpdateDTO body) {
        return Result.success(cinemaService.updateHall(hallId, body));
    }
}
