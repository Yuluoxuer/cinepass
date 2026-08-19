package com.cinepass.util;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * 锁座凭证 ID 生成：{@code lk} + UUID7（无连字符），与系分锁座前缀约定一致。
 */
public final class LockIds {

    private LockIds() {
    }

    /** 生成下一个锁座凭证 ID（总长 32，适配 seat_lock.lock_id VARCHAR(32)） */
    public static String next() {
        String hex = UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
        // "lk" + 30 hex = 32，与表字段宽度对齐
        return "lk" + hex.substring(0, 30);
    }
}
