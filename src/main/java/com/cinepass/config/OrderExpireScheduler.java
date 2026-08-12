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
 * 待支付订单超时清扫：周期性将已过支付截止的订单置为过期并释放锁座/座位。
 * <p>MVP 采用定时扫描（系分 §6.4，DB {@code expire_at} 为权威截止）；
 * 每批次限流避免大表全扫，单订单失败不阻塞其他订单。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ticket.expire-scan-enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpireScheduler {

    /** 单批最大处理数，防止积压一次性扫爆 */
    private static final int BATCH_SIZE = 200;

    private final OrderTicketMapper orderTicketMapper;
    private final OrderService orderService;

    public OrderExpireScheduler(OrderTicketMapper orderTicketMapper, OrderService orderService) {
        this.orderTicketMapper = orderTicketMapper;
        this.orderService = orderService;
    }

    /** 定时入口：默认每 30s 扫一次（可用 {@code ticket.expire-scan-interval-ms} 覆盖） */
    @Scheduled(fixedDelayString = "${ticket.expire-scan-interval-ms:30000}")
    public void scheduledExpire() {
        int processed = runOnce(BATCH_SIZE);
        if (processed > 0) {
            log.info("订单超时清扫完成，处理 {} 单", processed);
        }
    }

    /** 单次清扫：取候选并按订单逐个事务过期（便于测试与手动触发） */
    public int runOnce(int limit) {
        OffsetDateTime now = DateTimeFormats.now();
        List<String> expiredIds = orderTicketMapper.selectExpiredPendingPay(now, limit);
        int done = 0;
        for (String orderId : expiredIds) {
            try {
                // 每个订单独立事务：expireOrder 内部订单+锁座+座位同成功同回滚
                orderService.expireOrder(orderId);
                done++;
            } catch (Exception e) {
                // 单订单失败仅记日志，下轮重扫；不影响本批其他订单
                log.error("订单 {} 超时清扫失败: {}", orderId, e.getMessage(), e);
            }
        }
        return done;
    }
}
