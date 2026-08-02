package com.cinepass.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 当前请求登录用户（ThreadLocal）。userId 为字符串（u+uuid7）。
 */
@Component("securityContext")
public final class SecurityContext {

    private SecurityContext() {
    }

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<Context>();

    public static void set(String userId, String username, Long employeeId,
                           Long departmentId, List<String> roles, List<String> permissions) {
        Context existing = CONTEXT.get();
        String sid = existing != null ? existing.sid : null;
        String jti = existing != null ? existing.jti : null;
        String role = resolvePrimaryRole(roles);
        CONTEXT.set(new Context(userId, username, employeeId, departmentId, role, roles, permissions, sid, jti));
    }

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

    public static void clear() {
        CONTEXT.remove();
    }

    public static String getCurrentUserId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.userId : null;
    }

    public static String getCurrentUsername() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.username : null;
    }

    public static Long getCurrentEmployeeId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.employeeId : null;
    }

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

        Context(String userId, String username, Long employeeId,
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
