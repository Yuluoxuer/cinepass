package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 后台创建用户入参（含角色）。
 */
@Data
public class AdminUserCreateDTO {

    /** 昵称，全局唯一 */
    @NotBlank
    @Size(min = 1, max = 64)
    private String nickname;

    /** 手机号，可选；空串或合法大陆手机号 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 初始密码 */
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;

    /** 角色：user / staff / admin */
    @NotBlank
    @Pattern(regexp = "user|staff|admin")
    private String role;

    /** role=staff 时必填；user/admin 须为空 */
    @Size(max = 32)
    private String cinemaId;
}
