package com.cinepass.util;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * userId = "u" + UUID7（无连字符）。
 */
public final class UserIds {

    private UserIds() {
    }

    public static String next() {
        return "u" + UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
    }
}
