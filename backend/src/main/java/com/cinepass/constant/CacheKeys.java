package com.cinepass.constant;

/**
 * Redis Key 常量（系分 §6.5 auth）。
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** Refresh 会话：auth:refresh:{sid} */
    public static final String REFRESH_TOKEN = "auth:refresh:%s";

    /** Access jti 黑名单：auth:deny:{jti} */
    public static final String DENY_JTI = "auth:deny:%s";

    /** 登录失败计数：auth:fail:{username} */
    public static final String LOGIN_FAIL_COUNT = "auth:fail:%s";

    /** 登录锁定标记：auth:locked:{username} */
    public static final String LOGIN_LOCKED = "auth:locked:%s";

    /** 拼装 Refresh 会话 Key */
    public static String refreshTokenKey(String sid) {
        return String.format(REFRESH_TOKEN, sid);
    }

    /** 拼装 Access jti 黑名单 Key */
    public static String denyJtiKey(String jti) {
        return String.format(DENY_JTI, jti);
    }

    /** 拼装登录失败计数 Key */
    public static String failCountKey(String username) {
        return String.format(LOGIN_FAIL_COUNT, username);
    }

    /** 拼装登录锁定 Key */
    public static String lockedKey(String username) {
        return String.format(LOGIN_LOCKED, username);
    }
}
