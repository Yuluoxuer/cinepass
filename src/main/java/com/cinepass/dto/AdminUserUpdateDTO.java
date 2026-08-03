package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 后台更新用户入参；字段均为可选（传 null 表示不修改）。
 */
@Data
public class AdminUserUpdateDTO {

    /** 新昵称 */
    @Size(min = 1, max = 64)
    private String nickname;

    /** 新手机号；空串表示清空 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$")
    private String phone;

    /** 新角色：user / staff / admin */
    @Pattern(regexp = "user|staff|admin")
    private String role;

    /** staff 所属影院；改为 user/admin 时传空串清空 */
    @Size(max = 32)
    private String cinemaId;

    /** 账号状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 新密码；不传或空白则不改密 */
    @Size(min = 8, max = 64)
    private String password;
}
