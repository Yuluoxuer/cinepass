package com.cinepass.service;

import com.cinepass.vo.ShowSeatMapVO;

/**
 * 场次座位库存：播种 seat_status、组装购票座位图。
 */
public interface SeatInventoryService {

    /**
     * 确保场次座位库存已初始化；若 {@code seat_status} 为空则从 seat 表按 default_status 播种。
     * 幂等：已有行则不重复插入。
     */
    void ensureSeatStatus(String showId, String seatMapId);

    /**
     * 获取场次座位图（公开）；空库存时懒播种。
     * 稀疏 seats：仅返回 seat 表存在的格子，合并本场 seat_status。
     */
    ShowSeatMapVO getShowSeatMap(String showId);
}
