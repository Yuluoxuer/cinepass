package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.security.Staff;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 最小只读接口 — 仅供排片工作台加载影厅所绑座位图的分区信息。实际数据来自 seat_map + seat 表（座位管理模块负责）。
 */
@RestController
@RequestMapping("/api/v1/seat-maps")
@Staff
public class SeatMapController {

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable String id) {
        // 兜底：返回默认座位图结构，含一个 default 分区
        Map<String, Object> m = new HashMap<>();
        m.put("seatMapId", id);
        m.put("rows", 10);
        m.put("cols", 16);
        m.put("screenLabel", "银幕");
        m.put("zones", Arrays.asList("C"));
        m.put("seats", java.util.Collections.emptyList());
        m.put("mutable", true);
        return Result.success(m);
    }
}
