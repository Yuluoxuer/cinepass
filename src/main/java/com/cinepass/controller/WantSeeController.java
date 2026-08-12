package com.cinepass.controller;

import com.cinepass.aop.RateLimit;
import com.cinepass.common.Result;
import com.cinepass.security.LoginUser;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.WantSeeService;
import com.cinepass.vo.WantSeeVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 影片「想看」接口：加入 / 取消（需登录）。
 * <pre>
 * POST   /api/v1/movies/{movieId}/want-see
 * DELETE /api/v1/movies/{movieId}/want-see
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/movies")
public class WantSeeController {

    private final WantSeeService wantSeeService;

    public WantSeeController(WantSeeService wantSeeService) {
        this.wantSeeService = wantSeeService;
    }

    /** 将影片加入当前用户想看列表 */
    @RateLimit(key = "wantsee", permits = 10, windowSeconds = 60)
    @PostMapping("/{movieId}/want-see")
    @LoginUser
    public Result<WantSeeVO> add(@PathVariable String movieId) {
        return Result.success(wantSeeService.add(SecurityContext.getCurrentUserId(), movieId));
    }

    /** 取消当前用户对该影片的想看 */
    @RateLimit(key = "wantsee", permits = 10, windowSeconds = 60)
    @DeleteMapping("/{movieId}/want-see")
    @LoginUser
    public Result<WantSeeVO> remove(@PathVariable String movieId) {
        return Result.success(wantSeeService.remove(SecurityContext.getCurrentUserId(), movieId));
    }
}
