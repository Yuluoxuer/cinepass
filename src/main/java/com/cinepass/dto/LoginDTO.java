package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class LoginDTO {
    @NotBlank
    @Size(min = 1, max = 64)
    private String account;
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
