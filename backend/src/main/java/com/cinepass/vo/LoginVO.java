package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录/注册成功出参：Access Token 与用户基本信息。
 */
@Data
@Builder
public class LoginVO {

    /** Access Token */
    private String accessToken;

    /** Token 类型，固定 Bearer */
    private String tokenType;

    /** Access Token 有效期（秒） */
    private int expiresIn;

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
}
