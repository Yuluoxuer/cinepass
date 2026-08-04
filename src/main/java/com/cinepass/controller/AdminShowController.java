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

    @GetMapping
    public Result<ShowListResult> list(@RequestParam String cinemaId,
                                        @RequestParam String movieId,
                                        @RequestParam(required = false) String date) {
        if (date != null && !date.isEmpty()) {
            return Result.success(showService.list(cinemaId, movieId, date));
        }
        return Result.success(showService.listAll(cinemaId, movieId));
    }

    @PostMapping
    public Result<ShowVO> create(@Valid @RequestBody ShowCreateDTO body) {
        return Result.success(adminShowService.create(body));
    }

    @PutMapping("/{showId}")
    public Result<ShowVO> update(@PathVariable String showId,
                                  @Valid @RequestBody ShowUpdateDTO body) {
        return Result.success(adminShowService.update(showId, body));
    }

    @PostMapping("/{showId}/cancel")
    public Result<ShowVO> cancel(@PathVariable String showId) {
        return Result.success(adminShowService.cancel(showId));
    }

    @PostMapping("/{showId}/close-sale")
    public Result<ShowVO> closeSale(@PathVariable String showId) {
        return Result.success(adminShowService.closeSale(showId));
    }

    @PostMapping("/{showId}/resume-sale")
    public Result<ShowVO> resumeSale(@PathVariable String showId) {
        return Result.success(adminShowService.resumeSale(showId));
    }

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
