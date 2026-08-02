package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {
    private String accessToken;
    private String tokenType;
    private int expiresIn;
    private String userId;
    private String nickname;
    private String phone;
    private String role;
}
