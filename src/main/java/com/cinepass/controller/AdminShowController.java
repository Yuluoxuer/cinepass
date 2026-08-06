package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.ShowCreateDTO;
import com.cinepass.dto.ShowUpdateDTO;
import com.cinepass.security.Staff;
import com.cinepass.service.AdminShowService;
import com.cinepass.service.ShowService;
import com.cinepass.vo.ShowListResult;
import com.cinepass.vo.ShowVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import javax.validation.Valid;

/**
 * 运营端排片管理。
 * <pre>
 * GET  /api/v1/admin/shows
 * POST /api/v1/admin/shows
 * PUT  /api/v1/admin/shows/{showId}
 * POST .../cancel | close-sale | resume-sale
 * GET  .../impact
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/shows")
@Staff
public class AdminShowController {

    private final AdminShowService adminShowService;
    private final ShowService showService;

    public AdminShowController(AdminShowService adminShowService, ShowService showService) {
        this.adminShowService = adminShowService;
        this.showService = showService;
    }

    /** 排片列表；未传 movieId 则返回该院全部场次；未传 date 则返回全部日期 */
    @GetMapping
    public Result<ShowListResult> list(@RequestParam String cinemaId,
                                        @RequestParam(required = false) String movieId,
                                        @RequestParam(required = false) String date) {
        if (movieId == null || movieId.isEmpty()) {
            // 查询该影院所有场次（不限影片）
            return Result.success(showService.listAll(cinemaId, null));
        }
        if (date != null && !date.isEmpty()) {
            return Result.success(showService.list(cinemaId, movieId, date));
        }
        return Result.success(showService.listAll(cinemaId, movieId));
    }

    /** 新建场次；同厅时间冲突则 409 */
    @PostMapping
    public Result<ShowVO> create(@Valid @RequestBody ShowCreateDTO body) {
        return Result.success(adminShowService.create(body));
    }

    /** 改开场/散场或分区价；有在途锁座/订单时拒绝改时 */
    @PutMapping("/{showId}")
    public Result<ShowVO> update(@PathVariable String showId,
                                  @Valid @RequestBody ShowUpdateDTO body) {
        return Result.success(adminShowService.update(showId, body));
    }

    /** 取消场次 */
    @PostMapping("/{showId}/cancel")
    public Result<ShowVO> cancel(@PathVariable String showId) {
        return Result.success(adminShowService.cancel(showId));
    }

    /** 停售（保留场次，不可再购） */
    @PostMapping("/{showId}/close-sale")
    public Result<ShowVO> closeSale(@PathVariable String showId) {
        return Result.success(adminShowService.closeSale(showId));
    }

    /** 恢复开售 */
    @PostMapping("/{showId}/resume-sale")
    public Result<ShowVO> resumeSale(@PathVariable String showId) {
        return Result.success(adminShowService.resumeSale(showId));
    }

    /** 改期/取消影响面占位（订单统计待对接） */
    @GetMapping("/{showId}/impact")
    public Result<Map<String, Object>> impact(@PathVariable String showId) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("showId", showId);
        m.put("pendingPayCount", 0);
        m.put("issuedCount", 0);
        m.put("usedCount", 0);
        return Result.success(m);
    }
}
