package com.minihr.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前请求安全上下文（ThreadLocal）
 */
@Component("securityContext")
public final class SecurityContext {

    private SecurityContext() {
    }

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    public static void set(Long userId, String username, Long employeeId,
                           Long departmentId, List<String> roles, List<String> permissions) {
        CONTEXT.set(new Context(userId, username, employeeId, departmentId, roles, permissions));
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static Long getCurrentUserId() {
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

    public static List<String> getCurrentRoles() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.roles : new ArrayList<>();
    }

    public static List<String> getCurrentPermissions() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.permissions : new ArrayList<>();
    }

    public static boolean hasPermission(String permission) {
        List<String> perms = getCurrentPermissions();
        return perms.contains(permission) || perms.contains("admin");
    }

    private static class Context {
        final Long userId;
        final String username;
        final Long employeeId;
        final Long departmentId;
        final List<String> roles;
        final List<String> permissions;

        Context(Long userId, String username, Long employeeId,
                Long departmentId, List<String> roles, List<String> permissions) {
            this.userId = userId;
            this.username = username;
            this.employeeId = employeeId;
            this.departmentId = departmentId;
            this.roles = roles != null ? roles : new ArrayList<>();
            this.permissions = permissions != null ? permissions : new ArrayList<>();
        }
    }
}
