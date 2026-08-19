package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 修改密码入参。
 */
@Data
public class PasswordChangeDTO {

    /** 未登录时必填：昵称或手机号；已登录可省略 */
    @Size(max = 64)
    private String account;

    /** 旧密码 */
    @NotBlank
    @Size(min = 8, max = 64)
    private String oldPassword;

    /** 新密码 */
    @NotBlank
    @Size(min = 8, max = 64)
    private String newPassword;
}
