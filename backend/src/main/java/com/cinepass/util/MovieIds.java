package com.cinepass.util;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * movieId = "m" + UUID7（无连字符）。
 */
public final class MovieIds {

    private MovieIds() {
    }

    public static String next() {
        String uuid = UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
        return "m" + uuid.substring(0, 31);
    }
}
