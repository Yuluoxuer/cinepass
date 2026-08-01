package com.minihr.security; // 权限包

import org.springframework.stereotype.Component; // Bean 名 securityContext 供 SpEL

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 当前请求登录用户（ThreadLocal）。
 * 静态方法供 Java；Bean {@code securityContext} 供 @PreAuthorize SpEL。
 * <p>
 * 调用方：JwtAuthFilter 写入；Controller/Service/AuditLogAspect 读取。
 * Glob：已有 SecurityContext.java，覆盖加注释。无数据文件。
 * 用户指令：「给代码每行加上详细的注释」
 */
@Component("securityContext")
public final class SecurityContext {

    /** 禁止 new，只用静态方法 */
    private SecurityContext() {
    }

    /** 每线程一份；请求结束必须 clear */
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    /** Filter 写入用户基本信息；票务可把 employeeId/departmentId/permissions 传 null */
    public static void set(Long userId, String username, Long employeeId,
                           Long departmentId, List<String> roles, List<String> permissions) {
        Context existing = CONTEXT.get(); // 可能已有 sid/jti
        String sid = existing != null ? existing.sid : null;
        String jti = existing != null ? existing.jti : null;
        String role = resolvePrimaryRole(roles);
        CONTEXT.set(new Context(userId, username, employeeId, departmentId, role, roles, permissions, sid, jti));
    }

    /** Filter 补写会话 sid/jti/role */
    public static void setSession(String sid, String jti, String role) {
        Context existing = CONTEXT.get();
        if (existing == null) {
            CONTEXT.set(new Context(null, null, null, null, role, singletonRoleList(role), null, sid, jti));
            return;
        }
        CONTEXT.set(new Context(
                existing.userId, existing.username, existing.employeeId, existing.departmentId,
                role != null ? role : existing.role,
                existing.roles, existing.permissions, sid, jti));
    }

    /** 请求结束清理，防线程池串号 */
    public static void clear() {
        CONTEXT.remove();
    }

    /** 取 JWT 里的 userId；未登录为 null */
    public static Long getCurrentUserId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.userId : null;
    }

    public static String getCurrentUsername() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.username : null;
    }

    /** HR 遗留字段 */
    public static Long getCurrentEmployeeId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.employeeId : null;
    }

    /** HR 遗留字段 */
    public static Long getCurrentDepartmentId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.departmentId : null;
    }

    public static String getCurrentRole() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.role : null;
    }

    public static String getCurrentSid() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.sid : null;
    }

    public static String getCurrentJti() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.jti : null;
    }

    public static List<String> getCurrentRoles() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.roles : new ArrayList<String>();
    }

    public static List<String> getCurrentPermissions() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.permissions : new ArrayList<String>();
    }

    /** 权限码判断；admin 默认 true */
    public static boolean hasPermission(String permission) {
        List<String> perms = getCurrentPermissions();
        return perms.contains(permission) || Roles.ADMIN.equals(getCurrentRole());
    }

    public static boolean hasRole(String role) {
        String current = getCurrentRole();
        return current != null && current.equals(role);
    }

    public static boolean isAdmin() {
        return Roles.isAdmin(getCurrentRole());
    }

    public static boolean isStaffOrAdmin() {
        return Roles.isStaffOrAdmin(getCurrentRole());
    }

    private static String resolvePrimaryRole(List<String> roles) {
        if (roles != null && !roles.isEmpty()) {
            return roles.get(0);
        }
        return Roles.USER;
    }

    private static List<String> singletonRoleList(String role) {
        return role != null ? Collections.singletonList(role) : Collections.<String>emptyList();
    }

    /** ThreadLocal 快照 */
    private static class Context {
        final Long userId;
        final String username;
        final Long employeeId;
        final Long departmentId;
        final String role;
        final List<String> roles;
        final List<String> permissions;
        final String sid;
        final String jti;

        Context(Long userId, String username, Long employeeId,
                Long departmentId, String role, List<String> roles, List<String> permissions,
                String sid, String jti) {
            this.userId = userId;
            this.username = username;
            this.employeeId = employeeId;
            this.departmentId = departmentId;
            this.role = role != null ? role : Roles.USER;
            this.roles = roles != null ? roles : singletonRoleList(this.role);
            this.permissions = permissions != null ? permissions : new ArrayList<String>();
            this.sid = sid;
            this.jti = jti;
        }
    }
}
