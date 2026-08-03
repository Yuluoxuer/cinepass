package com.cinepass.util;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * 订单 ID 生成：{@code o} + UUID7（无连字符），与系分订单前缀约定一致。
 */
public final class OrderIds {

    private OrderIds() {
    }

    /** 生成下一个订单 ID */
    public static String next() {
        return "o" + UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
    }
}
