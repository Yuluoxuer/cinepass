package com.cinepass.service;

import com.cinepass.dto.RecommendSeatsDTO;
import com.cinepass.vo.SeatRecoResultVO;

/**
 * 智能选座（不锁座；系分 §4.3）。
 */
public interface SeatRecoService {

    /**
     * 在可售座位上搜索 Top-N 连座/偏好方案；最多 3 条。
     * 无解时 plans=[] 且 compromise 非空。
     */
    SeatRecoResultVO recommend(RecommendSeatsDTO dto);
}
