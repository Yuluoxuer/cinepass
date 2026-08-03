package com.cinepass.service;

import com.cinepass.dto.CancelOrderDTO;
import com.cinepass.dto.CreateOrderDTO;
import com.cinepass.vo.OrderVO;
import com.cinepass.vo.PageResult;

import java.time.LocalDate;

/**
 * 订单管理：创建 / 查询 / 取消 / 运营协助列表（系分 §6.1–6.5 / §10.10）。
 */
public interface OrderService {

    /**
     * 由有效锁座创建订单；同 lockId 幂等返回已有单。
     */
    OrderVO create(String userId, CreateOrderDTO dto);

    /** 本人订单详情 */
    OrderVO getMine(String userId, String orderId);

    /** 本人订单分页 */
    PageResult<OrderVO> listMine(String userId, String status, int page, int size);

    /**
     * 取消待支付订单并释放仍 active 的锁座。
     */
    OrderVO cancel(String userId, String orderId, CancelOrderDTO dto);

    /**
     * 运营协助查单：admin 全量；staff 仅本影院场次订单。
     */
    PageResult<OrderVO> listAdmin(String filterUserId, String status,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  int page, int size);
}
