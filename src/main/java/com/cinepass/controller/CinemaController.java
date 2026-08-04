package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.HallMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Hall;
import com.cinepass.vo.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小只读接口 — 仅供管理端排片工作台查询影院列表，非独立模块。
 */
@RestController
@RequestMapping("/api/v1/cinemas")
public class CinemaController {

    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;

    public CinemaController(CinemaMapper cinemaMapper, HallMapper hallMapper) {
        this.cinemaMapper = cinemaMapper;
        this.hallMapper = hallMapper;
    }

    @GetMapping
    public Result<PageResult<Object>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        // 简单全量返回，供前端 Select 下拉使用
        List<Cinema> all = cinemaMapper.listAll(0, 200);
        List<Object> items = new ArrayList<>();
        if (all != null) {
            for (Cinema c : all) {
                items.add(new CinemaBrief(c.getCinemaId(), c.getName(), c.getAddress()));
            }
        }
        return Result.success(new PageResult<>(items, page, size, items.size()));
    }

    @GetMapping("/{cinemaId}")
    public Result<Map<String, Object>> get(@PathVariable String cinemaId) {
        Cinema c = cinemaMapper.selectById(cinemaId);
        if (c == null) return Result.fail("影院不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("cinemaId", c.getCinemaId());
        m.put("name", c.getName());
        m.put("address", c.getAddress());
        m.put("distanceMeters", null);
        m.put("minPrice", null);
        m.put("trafficNote", null);
        List<Hall> halls = hallMapper.selectByCinemaId(cinemaId);
        List<Map<String, String>> hallBriefs = new ArrayList<>();
        if (halls != null) {
            for (Hall h : halls) {
                Map<String, String> hb = new HashMap<>();
                hb.put("hallId", h.getHallId());
                hb.put("name", h.getName());
                hallBriefs.add(hb);
            }
        }
        m.put("halls", hallBriefs);
        return Result.success(m);
    }

    @lombok.Value
    private static class CinemaBrief {
        String cinemaId;
        String name;
        String address;
    }
}
