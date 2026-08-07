package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 手机扫码后的订单摘要（系分 §6.2.2 · PaySessionVO）。
 * <p>支付页与核销页共用：按订单当前状态展示，前端据 {@code status} 控制确认按钮。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaySessionVO {

    /** 订单 ID */
    private String orderId;

    /** 订单应付金额（元） */
    private BigDecimal amount;

    /** 支付截止时间（ISO-8601）；已出票后为 null */
    private String expireAt;

    /** 影片标题快照 */
    private String movieTitle;

    /** 影院名称快照 */
    private String cinemaName;

    /** 影厅名称快照 */
    private String hallName;

    /** 开场时间（ISO-8601） */
    private String startTime;

    /** 系统 seatId 列表 */
    private List<String> seatIds;

    /** 座位号列表（如「6排7座」，取自座位价区快照） */
    private List<String> seatNames;

    /** 订单状态：pending_pay / issued / cancelled / redeemed */
    private String status;

    /** 取票码；待支付为空，出票后非空 */
    private String ticketCode;
}
