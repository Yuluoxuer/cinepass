package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.LoginDTO;
import com.cinepass.dto.PasswordChangeDTO;
import com.cinepass.dto.RegisterDTO;
import com.cinepass.security.LoginUser;
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

/**
 * 认证接口：登录 / 注册 / 改密 / 登出 / 当前用户。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录，返回 Access Token 与角色信息 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO body) {
        return Result.success(authService.login(body));
    }

    /** 注册普通用户并自动登录 */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO body) {
        return Result.success(authService.register(body));
    }

    /** 旧密码 + 新密码改密；可匿名（须带 account）或已登录 */
    @PostMapping("/password/change")
    public Result<Map<String, Boolean>> changePassword(@Valid @RequestBody PasswordChangeDTO body) {
        authService.changePassword(SecurityContext.getCurrentUserId(), body);
        // 已登录改密后吊销当前会话，强制用新密码重新登录（logout 对 null sid/jti 安全）
        authService.logout(SecurityContext.getCurrentSid(), SecurityContext.getCurrentJti());
        return Result.success(Collections.singletonMap("changed", true));
    }

    /** 登出当前会话 */
    @PostMapping("/logout")
    @LoginUser
    public Result<Map<String, Boolean>> logout() {
        authService.logout(SecurityContext.getCurrentSid(), SecurityContext.getCurrentJti());
        return Result.success(Collections.singletonMap("loggedOut", true));
    }

    /** 获取当前登录用户信息（含角色） */
    @GetMapping("/me")
    @LoginUser
    public Result<AuthMeVO> me() {
        return Result.success(authService.me(SecurityContext.getCurrentUserId()));
    }
}
