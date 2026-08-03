package com.cinepass.security;

/**
 * 票务中台角色常量（user / staff / admin）。
 * JWT claim、Spring {@code hasRole} 均使用这些小写字符串；Filter 会加 {@code ROLE_} 前缀。
 */
public final class Roles {

    private Roles() {
    }

    /** 普通购票用户 */
    public static final String USER = "user";

    /** 工作人员：运营配置、订单协助 */
    public static final String STAFF = "staff";

    /** 系统管理员：含账号与角色管理 */
    public static final String ADMIN = "admin";

    /** 判断是否为运营侧角色（staff 或 admin） */
    public static boolean isStaffOrAdmin(String role) {
        return STAFF.equals(role) || ADMIN.equals(role);
    }

    /** 判断是否为系统管理员 */
    public static boolean isAdmin(String role) {
        return ADMIN.equals(role);
    }
}
