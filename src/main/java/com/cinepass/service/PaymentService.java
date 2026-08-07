package com.cinepass.service;

import com.cinepass.dto.PayDTO;
import com.cinepass.vo.OrderVO;
import com.cinepass.vo.PayQrVO;
import com.cinepass.vo.PaySessionVO;
import com.cinepass.vo.RedeemQrVO;

/**
 * 模拟支付 + 核销模块（系分 §3.2.6 · 支付管理）。
 * <p>支付/核销接口仅供前端显式调用，禁止注册为 Agent Tool（防静默支付）。
 * 手机扫码免登录，靠二维码内签名 token 鉴权。</p>
 */
public interface PaymentService {

    /**
     * 为本人待支付订单签发支付二维码（payToken + H5 支付页 URL）。
     * <p>前置：订单存在、属于当前用户、状态 pending_pay 且未过支付截止；
     * 否则分别抛 NOT_FOUND / FORBIDDEN_PERMISSION / ORDER_NOT_PAYABLE / LOCK_EXPIRED。</p>
     */
    PayQrVO getPayQr(String userId, String orderId);

    /**
     * 手机扫码后拉取支付摘要（公开接口，凭 payToken 鉴权，无需 JWT）。
     * <p>token 验签失败或与 orderId 不匹配抛 PAY_TOKEN_INVALID；已过期抛 PAY_TOKEN_EXPIRED。</p>
     */
    PaySessionVO getPaySession(String orderId, String token);

    /**
     * 确认模拟支付并出票（唯一出票入口）。
     * <p>鉴权二选一：JWT 本人（{@code userId}）或 Header {@code X-Pay-Token}（{@code payToken}）。
     * 出票前重新校验订单仍 pending_pay 且未过支付截止，过期订单即使二维码仍可扫也拒绝。</p>
     */
    OrderVO pay(String orderId, String userId, String payToken, PayDTO dto);

    /**
     * 为本人已出票订单签发核销二维码（redeemToken + H5 核销页 URL）。
     * <p>仅 issued 可签发，否则抛 ORDER_NOT_REDEEMABLE。</p>
     */
    RedeemQrVO getRedeemQr(String userId, String orderId);

    /**
     * 手机扫码后拉取核销摘要（公开接口，凭 redeemToken 鉴权，无需 JWT）。
     */
    PaySessionVO getRedeemSession(String orderId, String token);

    /**
     * 确认核销：仅 issued 订单可转为 redeemed（已核销）。
     * <p>鉴权二选一：JWT 本人或 Header {@code X-Redeem-Token}。</p>
     */
    OrderVO redeem(String orderId, String userId, String redeemToken);
}
