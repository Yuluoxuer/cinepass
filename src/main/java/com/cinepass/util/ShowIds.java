package com.cinepass.util;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * showId = "s" + UUID7（无连字符）。
 */
public final class ShowIds {

    private ShowIds() {
    }

    public static String next() {
        String uuid = UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
        return "s" + uuid.substring(0, 31);
    }
}
