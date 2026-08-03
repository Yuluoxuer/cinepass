package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class AdminUserCreateDTO {
    @NotBlank
    @Size(min = 1, max = 64)
    private String nickname;
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
    @NotBlank
    @Pattern(regexp = "user|staff|admin")
    private String role;
    @Size(max = 32)
    private String cinemaId;
}
