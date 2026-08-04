package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 后台用户列表/详情出参；手机号已脱敏。
 */
@Data
@Builder
public class AdminUserVO {

    /** 用户 ID */
    private String userId;

    /** 昵称 */
    private String nickname;

    /** 脱敏后的手机号 */
    private String phone;

    /** 角色：user / staff / admin */
    private String role;

    /** staff 所属影院；user/admin 为 null */
    private String cinemaId;

    /** 账号状态：1 启用 / 0 禁用 */
    private int status;

    /** 创建时间（ISO-8601） */
    private String createdAt;
}
