package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.LoginDTO;
import com.cinepass.dto.PasswordChangeDTO;
import com.cinepass.dto.RegisterDTO;
import com.cinepass.security.LoginRequired;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.AuthService;
import com.cinepass.vo.AuthMeVO;
import com.cinepass.vo.LoginVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO body) {
        return Result.success(authService.login(body));
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO body) {
        return Result.success(authService.register(body));
    }

    /** 旧密码 + 新密码；可匿名（须带 account）或已登录 */
    @PostMapping("/password/change")
    public Result<Map<String, Boolean>> changePassword(@Valid @RequestBody PasswordChangeDTO body) {
        authService.changePassword(SecurityContext.getCurrentUserId(), body);
        return Result.success(Collections.singletonMap("changed", true));
    }

    @PostMapping("/logout")
    @LoginRequired
    public Result<Map<String, Boolean>> logout() {
        authService.logout(SecurityContext.getCurrentSid(), SecurityContext.getCurrentJti());
        return Result.success(Collections.singletonMap("loggedOut", true));
    }

    @GetMapping("/me")
    @LoginRequired
    public Result<AuthMeVO> me() {
        return Result.success(authService.me(SecurityContext.getCurrentUserId()));
    }
}
