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

    /**
     * 将已过支付截止的待支付订单置为过期并释放锁座/座位（定时清扫调用）。
     * <p>单订单内保证事务一致：订单状态 + 锁座 + 座位三者同成功或同回滚；
     * 已出票/已取消/已核销订单不受影响（条件更新幂等）。</p>
     */
    void expireOrder(String orderId);

    /**
     * 将场次已结束的已出票订单置为过期并标记「电影结束未使用」（定时清扫调用）。
     * <p>仅 issued 命中（条件更新幂等）；座位支付时已售出，无需释放。</p>
     */
    void expireUnusedOrder(String orderId);
}
