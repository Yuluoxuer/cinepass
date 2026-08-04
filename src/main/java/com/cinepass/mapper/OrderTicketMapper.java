package com.cinepass.mapper;

import com.cinepass.model.OrderTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 订单表 {@code order_ticket} Mapper。
 */
@Mapper
public interface OrderTicketMapper {

    /** 插入订单；依赖 lock_id 唯一索引做并发幂等 */
    int insert(OrderTicket order);

    /** 按订单 ID 查询 */
    OrderTicket findById(@Param("orderId") String orderId);

    /** 按订单 ID 查询并行锁（取消路径） */
    OrderTicket findByIdForUpdate(@Param("orderId") String orderId);

    /** 按锁座凭证查已有订单（创建幂等） */
    OrderTicket findByLockId(@Param("lockId") String lockId);

    /** 统计本人订单数；status 可空 */
    long countByUser(@Param("userId") String userId, @Param("status") String status);

    /** 分页查本人订单 */
    List<OrderTicket> listByUser(@Param("userId") String userId,
                                 @Param("status") String status,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    /** 运营协助统计；cinemaId 非空时限定本影院场次 */
    long countAdmin(@Param("userId") String userId,
                    @Param("status") String status,
                    @Param("dateFrom") LocalDate dateFrom,
                    @Param("dateTo") LocalDate dateTo,
                    @Param("cinemaId") String cinemaId);

    /** 运营协助分页列表 */
    List<OrderTicket> listAdmin(@Param("userId") String userId,
                                @Param("status") String status,
                                @Param("dateFrom") LocalDate dateFrom,
                                @Param("dateTo") LocalDate dateTo,
                                @Param("cinemaId") String cinemaId,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    /** 仅 pending_pay → cancelled；返回影响行数 */
    int updateCancelled(@Param("orderId") String orderId,
                        @Param("cancelReason") String cancelReason);
}
