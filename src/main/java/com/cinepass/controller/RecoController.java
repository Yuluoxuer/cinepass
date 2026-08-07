package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.RecommendSeatsDTO;
import com.cinepass.security.Public;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.RecoService;
import com.cinepass.service.SeatRecoService;
import com.cinepass.vo.PersonalRecoVO;
import com.cinepass.vo.SeatRecoResultVO;
import com.cinepass.vo.WeeklyHotVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 推荐接口（系分 §4；公开可读，与 SecurityConfig /reco/** 对齐）。
 * <pre>
 * GET  /api/v1/reco/weekly-hot 每周热门（公开）
 * GET  /api/v1/reco/personal    个人推荐（可选登录）
 * POST /api/v1/reco/seats       座位推荐
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/reco")
public class RecoController {

    private final SeatRecoService seatRecoService;
    private final RecoService recoService;

    public RecoController(SeatRecoService seatRecoService, RecoService recoService) {
        this.seatRecoService = seatRecoService;
        this.recoService = recoService;
    }

    /** 每周热门榜单；公开，热门分由算法物化得出 */
    @GetMapping("/weekly-hot")
    @Public
    public Result<WeeklyHotVO> weeklyHot(@RequestParam(required = false) String cityId,
                                         @RequestParam(required = false) Integer limit) {
        return Result.success(recoService.weeklyHot(cityId, limit));
    }

    /** 个人推荐；未登录回退热门榜，登录按用户画像打分 */
    @GetMapping("/personal")
    @Public
    public Result<PersonalRecoVO> personal(@RequestParam(required = false) Integer limit,
                                           @RequestParam(required = false) String excludeMovieIds) {
        return Result.success(recoService.personal(SecurityContext.getCurrentUserId(), limit, excludeMovieIds));
    }

    /** 智能选座；不锁座 */
    @PostMapping("/seats")
    @Public
    public Result<SeatRecoResultVO> recommendSeats(@Valid @RequestBody RecommendSeatsDTO dto) {
        return Result.success(seatRecoService.recommend(dto));
    }
}
