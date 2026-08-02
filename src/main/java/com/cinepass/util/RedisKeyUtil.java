package com.cinepass.util;

/**
 * Redis key naming helpers — extend with your own domain prefixes.
 */
public final class RedisKeyUtil {

    private RedisKeyUtil() {
    }

    /** Build a colon-separated key: prefix:part1:part2 */
    public static String key(String prefix, String... parts) {
        StringBuilder sb = new StringBuilder(prefix);
        for (String part : parts) {
            sb.append(':').append(part);
        }
        return sb.toString();
    }
}
