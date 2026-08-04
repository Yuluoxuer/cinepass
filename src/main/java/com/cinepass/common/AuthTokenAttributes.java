package com.cinepass.common;

/**
 * HTTP 请求属性名：认证过滤器写入的临时 Token 等。
 */
public final class AuthTokenAttributes {

    /** 静默续期后挂到 request 上的新 Access Token */
    public static final String RENEWED_ACCESS_TOKEN = "RENEWED_ACCESS_TOKEN";

    private AuthTokenAttributes() {
    }
}
