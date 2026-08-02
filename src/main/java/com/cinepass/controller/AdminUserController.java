package com.cinepass.controller;

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

@RestController
@RequestMapping("/api/v1/admin/users")
@Admin
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public Result<PageResult<AdminUserVO>> page(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminUserService.page(role, status, page, size));
    }

    @PostMapping
    public Result<AdminUserVO> create(@Valid @RequestBody AdminUserCreateDTO body) {
        return Result.success(adminUserService.create(body));
    }

    @PutMapping("/{userId}")
    public Result<AdminUserVO> update(@PathVariable String userId,
                                      @RequestBody AdminUserUpdateDTO body) {
        return Result.success(adminUserService.update(userId, body));
    }
}
