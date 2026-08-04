package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 用户账号表 {@code user_account} 映射。
 */
@Data
public class UserAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户 ID（u + UUID7） */
    private String userId;

    /** 昵称，唯一 */
    private String nickname;

    /** 手机号，可空、唯一 */
    private String phone;

    /** 密码哈希（BCrypt） */
    private String passwordHash;

    /** 角色：user / staff / admin */
    private String role;

    /** staff 所属影院 ID；user/admin 为 null */
    private String cinemaId;

    /**
     * MyBatis 部分更新标记：为 true 时写入 {@code cinema_id}（允许写 null 清空）。
     * 非表字段。
     */
    private Boolean updateCinemaId;

    /** 头像 URL */
    private String avatarUrl;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
