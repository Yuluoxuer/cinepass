package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class AdminUserUpdateDTO {
    @Size(min = 1, max = 64)
    private String nickname;
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$")
    private String phone;
    @Pattern(regexp = "user|staff|admin")
    private String role;
    private Integer status;
    @Size(min = 8, max = 64)
    private String password;
}
