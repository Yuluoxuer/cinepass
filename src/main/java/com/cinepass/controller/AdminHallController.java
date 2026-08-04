package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.mapper.HallMapper;
import com.cinepass.model.Hall;
import com.cinepass.security.Staff;
import com.cinepass.vo.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小只读接口 — 仅供排片工作台按影院查影厅列表。
 */
@RestController
@RequestMapping("/api/v1/admin/halls")
@Staff
public class AdminHallController {

    private final HallMapper hallMapper;

    public AdminHallController(HallMapper hallMapper) {
        this.hallMapper = hallMapper;
    }

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(@RequestParam String cinemaId,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        List<Hall> halls = hallMapper.selectByCinemaId(cinemaId);
        List<Map<String, Object>> items = new ArrayList<>();
        if (halls != null) {
            for (Hall h : halls) {
                Map<String, Object> item = new HashMap<>();
                item.put("hallId", h.getHallId());
                item.put("cinemaId", h.getCinemaId());
                item.put("name", h.getName());
                item.put("seatMapId", h.getSeatMapId());
                items.add(item);
            }
        }
        return Result.success(new PageResult<>(items, page, size, items.size()));
    }
}
