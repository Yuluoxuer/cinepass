package com.cinepass.util;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * 购票 Draft 会话 ID：{@code sess} + UUID7（无连字符）。
 */
public final class SessionIds {

    private SessionIds() {
    }

    /** 生成下一个 sessionId */
    public static String next() {
        return "sess" + UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
    }
}
