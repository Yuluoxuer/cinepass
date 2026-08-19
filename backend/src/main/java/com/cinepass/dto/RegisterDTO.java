package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 注册入参；注册成功后角色固定为 user。
 */
@Data
public class RegisterDTO {

    /** 昵称，全局唯一 */
    @NotBlank
    @Size(min = 1, max = 64)
    private String nickname;

    /** 大陆手机号，全局唯一 */
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 登录密码，8–64 位 */
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
