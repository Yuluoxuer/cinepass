package com.cinepass.constant;

/**
 * Redis Key 常量（系分 §6.5 auth）。
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static final String REFRESH_TOKEN = "auth:refresh:%s";
    public static final String DENY_JTI = "auth:deny:%s";
    public static final String LOGIN_FAIL_COUNT = "auth:fail:%s";
    public static final String LOGIN_LOCKED = "auth:locked:%s";

    public static String refreshTokenKey(String sid) {
        return String.format(REFRESH_TOKEN, sid);
    }

    public static String denyJtiKey(String jti) {
        return String.format(DENY_JTI, jti);
    }

    public static String failCountKey(String username) {
        return String.format(LOGIN_FAIL_COUNT, username);
    }

    public static String lockedKey(String username) {
        return String.format(LOGIN_LOCKED, username);
    }
}
