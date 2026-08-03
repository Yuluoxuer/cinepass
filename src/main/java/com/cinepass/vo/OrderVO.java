package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单对外展示对象（OrderVO）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {

    /** 订单 ID（o + UUID7） */
    private String orderId;

    /** 下单用户 ID */
    private String userId;

    /** 场次 ID */
    private String showId;

    /** 影片标题（下单时快照） */
    private String movieTitle;

    /** 影院名称（下单时快照） */
    private String cinemaName;

    /** 影厅名称（下单时快照） */
    private String hallName;

    /** 开场时间（ISO-8601） */
    private String startTime;

    /** 座位 ID 列表 */
    private List<String> seatIds;

    /** 均摊单价 */
    private BigDecimal unitPrice;

    /** 订单总金额 */
    private BigDecimal amount;

    /** 状态：pending_pay / issued / cancelled */
    private String status;

    /** 取票码；出票前可空 */
    private String ticketCode;

    /** 验票二维码载荷；出票前可空 */
    private String qrPayload;

    /** 关联锁座凭证 ID */
    private String lockId;

    /** 支付/锁座过期时间（ISO-8601） */
    private String expireAt;

    /** 创建时间（ISO-8601） */
    private String createdAt;

    /** 支付时间（ISO-8601）；未支付为 null */
    private String payAt;

    /** 支付渠道；未支付为 null */
    private String payChannel;
}
