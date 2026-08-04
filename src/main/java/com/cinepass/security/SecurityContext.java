package com.cinepass.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 当前请求登录用户上下文（ThreadLocal）。
 * 由 {@link JwtAuthFilter} 写入，请求结束时清理；供业务代码与 SpEL 读取。
 */
@Component("securityContext")
public final class SecurityContext {

    private SecurityContext() {
    }

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<Context>();

    /** 写入当前用户身份（userId / 用户名 / 角色列表 / 权限列表等） */
    public static void set(String userId, String username, Long employeeId,
                           Long departmentId, List<String> roles, List<String> permissions) {
        Context existing = CONTEXT.get();
        String sid = existing != null ? existing.sid : null;
        String jti = existing != null ? existing.jti : null;
        String cinemaId = existing != null ? existing.cinemaId : null;
        String role = resolvePrimaryRole(roles);
        CONTEXT.set(new Context(userId, username, employeeId, departmentId, role, roles, permissions, sid, jti, cinemaId));
    }

    /** 补充或更新会话信息（sid / jti / 主角色） */
    public static void setSession(String sid, String jti, String role) {
        Context existing = CONTEXT.get();
        if (existing == null) {
            CONTEXT.set(new Context(null, null, null, null, role, singletonRoleList(role), null, sid, jti, null));
            return;
        }
        CONTEXT.set(new Context(
                existing.userId, existing.username, existing.employeeId, existing.departmentId,
                role != null ? role : existing.role,
                existing.roles, existing.permissions, sid, jti, existing.cinemaId));
    }

    /** 写入当前 staff 所属影院（user/admin 可为 null） */
    public static void setCinemaId(String cinemaId) {
        Context existing = CONTEXT.get();
        if (existing == null) {
            CONTEXT.set(new Context(null, null, null, null, Roles.USER, singletonRoleList(Roles.USER),
                    null, null, null, cinemaId));
            return;
        }
        CONTEXT.set(new Context(
                existing.userId, existing.username, existing.employeeId, existing.departmentId,
                existing.role, existing.roles, existing.permissions, existing.sid, existing.jti, cinemaId));
    }

    /** 清理当前线程上下文，防止线程复用泄漏 */
    public static void clear() {
        CONTEXT.remove();
    }

    /** 获取当前登录用户 ID */
    public static String getCurrentUserId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.userId : null;
    }

    /** 获取当前登录用户名（昵称） */
    public static String getCurrentUsername() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.username : null;
    }

    /** 获取当前员工 ID（票务中台暂未使用，保留字段） */
    public static Long getCurrentEmployeeId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.employeeId : null;
    }

    /** 获取当前部门 ID（票务中台暂未使用，保留字段） */
    public static Long getCurrentDepartmentId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.departmentId : null;
    }

    /** 获取当前主角色（user / staff / admin） */
    public static String getCurrentRole() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.role : null;
    }

    /** 获取当前会话 ID（sid） */
    public static String getCurrentSid() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.sid : null;
    }

    /** 获取当前 staff 所属影院 ID；user/admin 多为 null */
    public static String getCurrentCinemaId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.cinemaId : null;
    }

    /** 获取当前 Access Token 的 jti */
    public static String getCurrentJti() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.jti : null;
    }

    /** 获取当前角色列表（副本语义：无上下文时返回空列表） */
    public static List<String> getCurrentRoles() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.roles : new ArrayList<String>();
    }

    /** 获取当前权限码列表（当前 JWT 未注入权限，多为空） */
    public static List<String> getCurrentPermissions() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.permissions : new ArrayList<String>();
    }

    /** 判断是否具备指定权限码；admin 默认放行 */
    public static boolean hasPermission(String permission) {
        List<String> perms = getCurrentPermissions();
        return perms.contains(permission) || Roles.ADMIN.equals(getCurrentRole());
    }

    /** 判断当前主角色是否等于指定角色 */
    public static boolean hasRole(String role) {
        String current = getCurrentRole();
        return current != null && current.equals(role);
    }

    /** 判断当前用户是否为管理员 */
    public static boolean isAdmin() {
        return Roles.isAdmin(getCurrentRole());
    }

    /** 判断当前用户是否为工作人员或管理员 */
    public static boolean isStaffOrAdmin() {
        return Roles.isStaffOrAdmin(getCurrentRole());
    }

    /** 从角色列表取主角色，缺省为 user */
    private static String resolvePrimaryRole(List<String> roles) {
        if (roles != null && !roles.isEmpty()) {
            return roles.get(0);
        }
        return Roles.USER;
    }

    /** 将单个角色包装为不可变列表 */
    private static List<String> singletonRoleList(String role) {
        return role != null ? Collections.singletonList(role) : Collections.<String>emptyList();
    }

    /** ThreadLocal 中存放的请求级上下文快照 */
    private static class Context {
        final String userId;
        final String username;
        final Long employeeId;
        final Long departmentId;
        final String role;
        final List<String> roles;
        final List<String> permissions;
        final String sid;
        final String jti;
        final String cinemaId;

        Context(String userId, String username, Long employeeId,
                Long departmentId, String role, List<String> roles, List<String> permissions,
                String sid, String jti, String cinemaId) {
            this.userId = userId;
            this.username = username;
            this.employeeId = employeeId;
            this.departmentId = departmentId;
            this.role = role != null ? role : Roles.USER;
            this.roles = roles != null ? roles : singletonRoleList(this.role);
            this.permissions = permissions != null ? permissions : new ArrayList<String>();
            this.sid = sid;
            this.jti = jti;
            this.cinemaId = cinemaId;
        }
    }
}
