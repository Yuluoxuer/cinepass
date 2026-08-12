package com.cinepass.controller;

import com.cinepass.aop.RateLimit;
import com.cinepass.common.Result;
import com.cinepass.dto.AdminUserCreateDTO;
import com.cinepass.dto.AdminUserUpdateDTO;
import com.cinepass.security.Admin;
import com.cinepass.service.AdminUserService;
import com.cinepass.vo.AdminUserVO;
import com.cinepass.vo.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 后台用户管理 REST 接口，仅管理员可访问。
 * <pre>
 * GET  /api/v1/admin/users          分页列表
 * POST /api/v1/admin/users          创建用户
 * PUT  /api/v1/admin/users/{userId} 更新用户
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Admin
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** 分页查询用户，可按 role、status 筛选。 */
    @GetMapping
    public Result<PageResult<AdminUserVO>> page(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminUserService.page(role, status, page, size));
    }

    /** 创建用户（含角色）。 */
    @RateLimit(key = "admin-user", permits = 10, windowSeconds = 60)
    @PostMapping
    public Result<AdminUserVO> create(@Valid @RequestBody AdminUserCreateDTO body) {
        return Result.success(adminUserService.create(body));
    }

    /** 更新用户资料 / 角色 / 状态 / 密码（字段可选）。 */
    @RateLimit(key = "admin-user", permits = 10, windowSeconds = 60)
    @PutMapping("/{userId}")
    public Result<AdminUserVO> update(@PathVariable String userId,
                                      @RequestBody AdminUserUpdateDTO body) {
        return Result.success(adminUserService.update(userId, body));
    }
}
