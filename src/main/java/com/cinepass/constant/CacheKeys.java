package com.minihr.constant;

/**
 * Redis 缓存 Key 常量（通用键，不含业务域前缀）
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static final String LOGIN_FAIL_COUNT = "auth:fail:%s";
    public static final String LOGIN_LOCKED = "auth:locked:%s";
    public static final String REFRESH_TOKEN = "auth:refresh:%d";
    public static final String USER_PERMS = "user:perms:%d";
    public static final String USER_SCOPE = "user:scope:%d";
    public static final String USER_FIELD_PERMS = "user:field_perms:%d";
    public static final String USER_MENU = "user:menu:%d";
    public static final String EXPORT_TASK = "export:task:%s";

    public static String failCountKey(String username) {
        return String.format(LOGIN_FAIL_COUNT, username);
    }

    public static String lockedKey(String username) {
        return String.format(LOGIN_LOCKED, username);
    }

    public static String refreshTokenKey(Long userId) {
        return String.format(REFRESH_TOKEN, userId);
    }

    public static String userPermsKey(Long userId) {
        return String.format(USER_PERMS, userId);
    }

    public static String userScopeKey(Long userId) {
        return String.format(USER_SCOPE, userId);
    }

    public static String userFieldPermsKey(Long userId) {
        return String.format(USER_FIELD_PERMS, userId);
    }

    public static String userMenuKey(Long userId) {
        return String.format(USER_MENU, userId);
    }

    public static String exportTaskKey(String taskId) {
        return String.format(EXPORT_TASK, taskId);
    }
}
