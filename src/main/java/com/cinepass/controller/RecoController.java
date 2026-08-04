package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.RecommendSeatsDTO;
import com.cinepass.security.Public;
import com.cinepass.service.SeatRecoService;
import com.cinepass.vo.SeatRecoResultVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 推荐接口（座位推荐公开可读，与 SecurityConfig /reco/** 对齐）。
 * <pre>
 * POST /api/v1/reco/seats
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/reco")
public class RecoController {

    private final SeatRecoService seatRecoService;

    public RecoController(SeatRecoService seatRecoService) {
        this.seatRecoService = seatRecoService;
    }

    /** 智能选座；不锁座 */
    @PostMapping("/seats")
    @Public
    public Result<SeatRecoResultVO> recommendSeats(@Valid @RequestBody RecommendSeatsDTO dto) {
        return Result.success(seatRecoService.recommend(dto));
    }
}
