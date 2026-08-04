package com.cinepass.util;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * 用户 ID 生成：{@code u} + UUID7（无连字符）。
 */
public final class UserIds {

    private UserIds() {
    }

    /** 生成下一个用户 ID */
    public static String next() {
        String uuid = UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
        return "u" + uuid.substring(0, 31);
    }
}
