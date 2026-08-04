package com.cinepass.util;

import com.github.f4b6a3.uuid.UuidCreator;

public final class CinemaIds {
    private CinemaIds() {
    }

    public static String nextCinemaId() {
        return next("c");
    }

    public static String nextHallId() {
        return next("h");
    }

    public static String nextSeatMapId() {
        return next("sm");
    }

    private static String next(String prefix) {
        String uuid = UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
        return prefix + uuid.substring(0, 32 - prefix.length());
    }
}
