package com.minihr.security; // 权限包

/**
 * 票务中台角色常量（系分 §10.0）。
 * JWT claim role、Spring hasRole 都用这些小写字符串；Filter 会加 ROLE_ 前缀。
 * <p>
 * 调用方：JwtUtil、JwtAuthFilter、SecurityConfig L103-109、SecurityContext、@Admin/@Staff。
 * Glob：已有 Roles.java，本操作覆盖加注释。无数据文件。
 * 用户指令：「给代码每行加上详细的注释」
 */
public final class Roles {

    /** 工具类不允许 new */
    private Roles() {
    }

    /** 普通购票用户 */
    public static final String USER = "user";

    /** 工作人员：运营配置、订单协助 */
    public static final String STAFF = "staff";

    /** 系统管理员：含账号管理 */
    public static final String ADMIN = "admin";

    /** 是否运营侧（staff 或 admin）；常量在前避免 role 为 null 时 NPE */
    public static boolean isStaffOrAdmin(String role) {
        return STAFF.equals(role) || ADMIN.equals(role);
    }

    /** 是否系统管理员 */
    public static boolean isAdmin(String role) {
        return ADMIN.equals(role);
    }
}
