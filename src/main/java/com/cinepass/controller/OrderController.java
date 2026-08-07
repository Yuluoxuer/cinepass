package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.CancelOrderDTO;
import com.cinepass.dto.CreateOrderDTO;
import com.cinepass.dto.PayDTO;
import com.cinepass.security.LoginUser;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.OrderService;
import com.cinepass.service.PaymentService;
import com.cinepass.vo.OrderVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.PayQrVO;
import com.cinepass.vo.PaySessionVO;
import com.cinepass.vo.RedeemQrVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 用户订单接口（系分订单管理 · OrderAgent 对应 REST）。
 * <pre>
 * POST /api/v1/orders                 创建
 * GET  /api/v1/orders                 本人列表
 * GET  /api/v1/orders/{orderId}       详情
 * POST /api/v1/orders/{orderId}/cancel 取消
 * GET  /api/v1/orders/{orderId}/pay-qrcode 支付二维码
 * GET  /api/v1/orders/{orderId}/pay-session 扫码支付摘要（公开，验 payToken）
 * POST /api/v1/orders/{orderId}/pay 确认支付出票（JWT 或 X-Pay-Token）
 * GET  /api/v1/orders/{orderId}/redeem-qrcode 核销二维码
 * GET  /api/v1/orders/{orderId}/redeem-session 扫码核销摘要（公开，验 redeemToken）
 * POST /api/v1/orders/{orderId}/redeem 确认核销（JWT 或 X-Redeem-Token）
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    /** 由有效锁座创建订单；同 lockId 幂等返回已有单 */
    @PostMapping
    @LoginUser
    public Result<OrderVO> create(@Valid @RequestBody CreateOrderDTO dto) {
        return Result.success(orderService.create(SecurityContext.getCurrentUserId(), dto));
    }

    /** 本人订单分页；可选按 status 过滤 */
    @GetMapping
    @LoginUser
    public Result<PageResult<OrderVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.listMine(SecurityContext.getCurrentUserId(), status, page, size));
    }

    /** 本人订单详情；非本人返回无权限 */
    @GetMapping("/{orderId}")
    @LoginUser
    public Result<OrderVO> get(@PathVariable String orderId) {
        return Result.success(orderService.getMine(SecurityContext.getCurrentUserId(), orderId));
    }

    /** 取消待支付订单并释放锁座；已取消幂等返回 */
    @PostMapping("/{orderId}/cancel")
    @LoginUser
    public Result<OrderVO> cancel(@PathVariable String orderId,
                                  @RequestBody(required = false) CancelOrderDTO dto) {
        return Result.success(orderService.cancel(SecurityContext.getCurrentUserId(), orderId, dto));
    }

    /** 生成支付二维码：签发短期 payToken 并返回 H5 支付页 URL；仅本人待支付订单 */
    @GetMapping("/{orderId}/pay-qrcode")
    @LoginUser
    public Result<PayQrVO> payQrcode(@PathVariable String orderId) {
        return Result.success(paymentService.getPayQr(SecurityContext.getCurrentUserId(), orderId));
    }

    /** 手机扫码后拉取支付摘要；公开接口，凭 URL 内 payToken 鉴权 */
    @GetMapping("/{orderId}/pay-session")
    public Result<PaySessionVO> paySession(@PathVariable String orderId,
                                           @RequestParam String t) {
        return Result.success(paymentService.getPaySession(orderId, t));
    }

    /** 确认模拟支付并出票；鉴权二选一：本人 JWT 或 Header X-Pay-Token */
    @PostMapping("/{orderId}/pay")
    public Result<OrderVO> pay(@PathVariable String orderId,
                               @RequestHeader(value = "X-Pay-Token", required = false) String payToken,
                               @RequestBody(required = false) PayDTO dto) {
        return Result.success(paymentService.pay(orderId, SecurityContext.getCurrentUserId(), payToken, dto));
    }

    /** 生成核销二维码：签发短期 redeemToken 并返回 H5 核销页 URL；仅本人已出票订单 */
    @GetMapping("/{orderId}/redeem-qrcode")
    @LoginUser
    public Result<RedeemQrVO> redeemQrcode(@PathVariable String orderId) {
        return Result.success(paymentService.getRedeemQr(SecurityContext.getCurrentUserId(), orderId));
    }

    /** 手机扫码后拉取核销摘要；公开接口，凭 URL 内 redeemToken 鉴权 */
    @GetMapping("/{orderId}/redeem-session")
    public Result<PaySessionVO> redeemSession(@PathVariable String orderId,
                                              @RequestParam String t) {
        return Result.success(paymentService.getRedeemSession(orderId, t));
    }

    /** 确认核销：仅已出票订单可转为已核销；鉴权二选一：本人 JWT 或 Header X-Redeem-Token */
    @PostMapping("/{orderId}/redeem")
    public Result<OrderVO> redeem(@PathVariable String orderId,
                                  @RequestHeader(value = "X-Redeem-Token", required = false) String redeemToken) {
        return Result.success(paymentService.redeem(orderId, SecurityContext.getCurrentUserId(), redeemToken));
    }
}
