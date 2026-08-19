package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 登录入参。
 */
@Data
public class LoginDTO {

    /** 账号：昵称或手机号 */
    @NotBlank
    @Size(min = 1, max = 64)
    private String account;

    /** 登录密码 */
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
