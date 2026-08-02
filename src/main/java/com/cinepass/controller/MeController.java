package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.ProfileUpdateDTO;
import com.cinepass.security.LoginRequired;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.ProfileService;
import com.cinepass.service.WantSeeService;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.ProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ProfileService profileService;
    private final WantSeeService wantSeeService;

    public MeController(ProfileService profileService, WantSeeService wantSeeService) {
        this.profileService = profileService;
        this.wantSeeService = wantSeeService;
    }

    @GetMapping("/profile")
    @LoginRequired
    public Result<ProfileVO> getProfile() {
        return Result.success(profileService.get(SecurityContext.getCurrentUserId()));
    }

    @PutMapping("/profile")
    @LoginRequired
    public Result<ProfileVO> updateProfile(@RequestBody ProfileUpdateDTO body) {
        return Result.success(profileService.update(SecurityContext.getCurrentUserId(), body));
    }

    @GetMapping("/want-see")
    @LoginRequired
    public Result<PageResult<MovieVO>> wantSee(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(wantSeeService.list(SecurityContext.getCurrentUserId(), page, size));
    }
}
