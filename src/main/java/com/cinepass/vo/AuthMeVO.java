package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthMeVO {
    private String userId;
    private String nickname;
    private String phone;
    private String role;
    private String avatarUrl;
}
