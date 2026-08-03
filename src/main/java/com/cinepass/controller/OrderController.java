package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.CancelOrderDTO;
import com.cinepass.dto.CreateOrderDTO;
import com.cinepass.security.LoginUser;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.OrderService;
import com.cinepass.vo.OrderVO;
import com.cinepass.vo.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
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
}
