package com.cinepass.constant;

/**
 * 锁座状态常量（seat_lock.status）。
 */
public final class LockStatus {

    /** 有效锁定中 */
    public static final String ACTIVE = "active";

    /** 已过期 */
    public static final String EXPIRED = "expired";

    /** 已消费（下单占用） */
    public static final String CONSUMED = "consumed";

    /** 已主动释放（取消订单等） */
    public static final String RELEASED = "released";

    private LockStatus() {
    }
}
