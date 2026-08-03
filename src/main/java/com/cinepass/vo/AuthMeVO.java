package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 当前登录用户信息出参（GET /auth/me）。
 */
@Data
@Builder
public class AuthMeVO {

    /** 用户 ID */
    private String userId;

    /** 昵称 */
    private String nickname;

    /** 脱敏手机号 */
    private String phone;

    /** 角色：user / staff / admin */
    private String role;

    /** staff 所属影院；user/admin 为 null */
    private String cinemaId;

    /** 头像 URL */
    private String avatarUrl;
}
