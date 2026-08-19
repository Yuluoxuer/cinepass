package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.security.Staff;
import com.cinepass.service.DashboardService;
import com.cinepass.vo.AdminDashboardStatsVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 运营 Dashboard 统计。
 * <pre>
 * GET /api/v1/admin/dashboard/stats?date=YYYY-MM-DD
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Staff
public class AdminDashboardController {

    private final DashboardService dashboardService;

    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public Result<AdminDashboardStatsVO> stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(dashboardService.getStats(date));
    }
}
