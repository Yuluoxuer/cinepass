package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 核销二维码返回体。
 * <p>前端将 {@code redeemUrl} 编码为二维码；手机扫码打开 H5 核销页（免登录，凭 URL 内 token）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedeemQrVO {

    /** 订单 ID */
    private String orderId;

    /** 取票码（出票时生成） */
    private String ticketCode;

    /** H5 核销页 URL（含短期 redeemToken） */
    private String redeemUrl;
}
