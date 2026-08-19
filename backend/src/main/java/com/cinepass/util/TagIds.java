package com.cinepass.util;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * tagId = "t" + UUID7（无连字符）。
 */
public final class TagIds {

    private TagIds() {
    }

    public static String next() {
        String uuid = UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
        return "t" + uuid.substring(0, 31);
    }
}
