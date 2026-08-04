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

    private OrderStatus() {
    }
}
