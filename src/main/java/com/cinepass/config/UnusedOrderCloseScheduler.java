package com.cinepass.config;

import com.cinepass.mapper.OrderTicketMapper;
import com.cinepass.service.OrderService;
import com.cinepass.util.DateTimeFormats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 电影结束未使用订单清扫：周期性将已出票且场次已结束的订单置为过期。
 * <p>复用已有 {@code expired} 状态（前端映射零改动），以 {@code cancel_reason='unused_after_show'}
 * 与支付超时（{@code pay_timeout}）在数据层区分；座位支付时已售出，无需释放。</p>
 * <p>仅处理 issued 订单，与支付超时清扫（{@link OrderExpireScheduler}）互不重叠；
 * 每批次限流避免大表全扫，单订单失败不阻塞其他订单。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ticket.unused-scan-enabled", havingValue = "true", matchIfMissing = true)
public class UnusedOrderCloseScheduler {

    /** 单批最大处理数，防止积压一次性扫爆 */
    private static final int BATCH_SIZE = 200;

    private final OrderTicketMapper orderTicketMapper;
    private final OrderService orderService;

    public UnusedOrderCloseScheduler(OrderTicketMapper orderTicketMapper, OrderService orderService) {
        this.orderTicketMapper = orderTicketMapper;
        this.orderService = orderService;
    }

    /** 定时入口：默认每 60s 扫一次（可用 {@code ticket.unused-scan-interval-ms} 覆盖） */
    @Scheduled(fixedDelayString = "${ticket.unused-scan-interval-ms:60000}")
    public void scheduledClose() {
        int processed = runOnce(BATCH_SIZE);
        if (processed > 0) {
            log.info("电影结束未使用订单清扫完成，处理 {} 单", processed);
        }
    }

    /** 单次清扫：取候选并按订单逐个事务置过期（便于测试与手动触发） */
    public int runOnce(int limit) {
        OffsetDateTime now = DateTimeFormats.now();
        List<String> endedOrderIds = orderTicketMapper.selectIssuedEndedOrders(now, limit);
        int done = 0;
        for (String orderId : endedOrderIds) {
            try {
                // 每个订单独立事务：条件更新仅 issued 命中，同成功同回滚
                orderService.expireUnusedOrder(orderId);
                done++;
            } catch (Exception e) {
                // 单订单失败仅记日志，下轮重扫；不影响本批其他订单
                log.error("订单 {} 电影结束未使用清扫失败: {}", orderId, e.getMessage(), e);
            }
        }
        return done;
    }
}
