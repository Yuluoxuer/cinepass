package com.cinepass.util;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * 影院 / 影厅 / 座位图业务 ID 生成（前缀 + UUID7 无连字符，总长 32）。
 */
public final class CinemaIds {

    private CinemaIds() {
    }

    /** cinemaId = {@code c} + UUID7 */
    public static String nextCinemaId() {
        return next("c");
    }

    /** hallId = {@code h} + UUID7 */
    public static String nextHallId() {
        return next("h");
    }

    /** seatMapId = {@code sm} + UUID7 */
    public static String nextSeatMapId() {
        return next("sm");
    }

    private static String next(String prefix) {
        String uuid = UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
        return prefix + uuid.substring(0, 32 - prefix.length());
    }
}
