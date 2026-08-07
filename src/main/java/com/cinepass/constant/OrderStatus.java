package com.cinepass.constant;

/**
 * 订单状态常量（order_ticket.status）。
 */
public final class OrderStatus {

    /** 待支付 */
    public static final String PENDING_PAY = "pending_pay";

    /** 已出票 */
    public static final String ISSUED = "issued";

    /** 已取消 */
    public static final String CANCELLED = "cancelled";

    /** 已核销 */
    public static final String REDEEMED = "redeemed";

    /** 支付超时已过期（定时清扫标记，锁座与座位已释放） */
    public static final String EXPIRED = "expired";

    private OrderStatus() {
    }
}
