package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付二维码返回体（系分 §6.2.1 · PayQrVO）。
 * <p>前端将 {@code payUrl} 编码为二维码；PC 按 {@code pollIntervalMs} 轮询订单状态直至 issued。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayQrVO {

    /** 订单 ID */
    private String orderId;

    /** 订单应付金额（元） */
    private BigDecimal amount;

    /** 支付截止时间（ISO-8601；等于锁座过期时间） */
    private String expireAt;

    /** H5 支付页 URL（含短期 payToken）；供 PC 编码为支付二维码 */
    private String payUrl;

    /** PC 轮询订单状态建议间隔（毫秒） */
    private Integer pollIntervalMs;
}
