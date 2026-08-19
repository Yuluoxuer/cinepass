package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.security.Staff;
import com.cinepass.service.OrderService;
import com.cinepass.vo.OrderVO;
import com.cinepass.vo.PageResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 运营协助查单（系分 §10.10）。staff 仅本影院；admin 全量。只读。
 * <pre>
 * GET /api/v1/admin/orders
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@Staff
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 运营协助查单：可按用户、状态、日期筛选；staff 自动限定本影院 */
    @GetMapping
    public Result<PageResult<OrderVO>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.listAdmin(userId, status, dateFrom, dateTo, page, size));
    }
}
