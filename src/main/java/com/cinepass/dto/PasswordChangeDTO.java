package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class PasswordChangeDTO {
    /** 未登录时必填：昵称或手机号；已登录可省略 */
    @Size(max = 64)
    private String account;

    @NotBlank
    @Size(min = 8, max = 64)
    private String oldPassword;

    @NotBlank
    @Size(min = 8, max = 64)
    private String newPassword;
}
