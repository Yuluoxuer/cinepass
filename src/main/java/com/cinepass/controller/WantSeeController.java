package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.security.LoginRequired;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.WantSeeService;
import com.cinepass.vo.WantSeeVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movies")
public class WantSeeController {

    private final WantSeeService wantSeeService;

    public WantSeeController(WantSeeService wantSeeService) {
        this.wantSeeService = wantSeeService;
    }

    @PostMapping("/{movieId}/want-see")
    @LoginRequired
    public Result<WantSeeVO> add(@PathVariable String movieId) {
        return Result.success(wantSeeService.add(SecurityContext.getCurrentUserId(), movieId));
    }

    @DeleteMapping("/{movieId}/want-see")
    @LoginRequired
    public Result<WantSeeVO> remove(@PathVariable String movieId) {
        return Result.success(wantSeeService.remove(SecurityContext.getCurrentUserId(), movieId));
    }
}
