package com.cinepass.service;

import com.cinepass.vo.AdminDashboardStatsVO;

import java.time.LocalDate;

public interface DashboardService {
    AdminDashboardStatsVO getStats(LocalDate date);
}
