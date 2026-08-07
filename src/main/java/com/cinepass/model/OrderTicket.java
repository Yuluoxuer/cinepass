package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 订单表 {@code order_ticket} 映射。
 */
@Data
public class OrderTicket implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 订单 ID（o + UUID7） */
    private String orderId;

    /** 下单用户 ID */
    private String userId;

    /** 场次 ID */
    private String showId;

    /** 关联锁座凭证 ID（唯一，用于创建幂等） */
    private String lockId;

    /** 影片标题快照 */
    private String movieTitle;

    /** 影院名称快照 */
    private String cinemaName;

    /** 影厅名称快照 */
    private String hallName;

    /** 开场时间快照 */
    private OffsetDateTime startTime;

    /** 系统 seatId 列表 JSON */
    private String seatIdsJson;

    /** 均摊单价 */
    private BigDecimal unitPrice;

    /** 订单总金额 */
    private BigDecimal amount;

    /** [{seatId,zone,price,seatName?},...] JSON */
    private String seatPriceSnapshot;

    /** 状态：pending_pay / issued / cancelled / redeemed / expired */
    private String status;

    /** 取票码；出票前可空 */
    private String ticketCode;

    /** 验票二维码载荷；出票前可空 */
    private String qrPayload;

    /** 支付渠道；未支付为 null */
    private String payChannel;

    /** 支付截止时间（通常等于锁座过期时间） */
    private OffsetDateTime expireAt;

    /** 支付完成时间 */
    private OffsetDateTime payAt;

    /** 取消原因，如 user_cancel */
    private String cancelReason;

    /** Agent/会话 ID，可选 */
    private String sessionId;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
