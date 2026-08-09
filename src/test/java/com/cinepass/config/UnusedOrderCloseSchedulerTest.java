package com.cinepass.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 电影结束未使用订单清扫集成测试：场次已结束的 issued 订单 → expired(unused_after_show)。
 * <p>直接通过 jdbcTemplate 构造 show_schedule + order_ticket 行，验证候选查询与条件更新 SQL。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnusedOrderCloseSchedulerTest {

    private static final ZoneOffset CST = ZoneOffset.ofHours(8);

    @Autowired
    private UnusedOrderCloseScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runOnce_issuedOrderAfterShowEnd_shouldBecomeExpiredUnused() {
        OffsetDateTime now = OffsetDateTime.now(CST);
        insertShow("s_end_1", now.minusHours(3), now.minusHours(1)); // 场次已结束
        insertOrder("o_end_1", "s_end_1", now.minusHours(2), "issued");

        int done = scheduler.runOnce(100);

        assertThat(done).isEqualTo(1);
        assertOrder("o_end_1", "expired", "unused_after_show");
    }

    @Test
    void runOnce_issuedOrderBeforeShowEnd_shouldStayIssued() {
        OffsetDateTime now = OffsetDateTime.now(CST);
        insertShow("s_future_1", now.plusHours(1), now.plusHours(3)); // 场次未结束
        insertOrder("o_future_1", "s_future_1", now.plusHours(2), "issued");

        int done = scheduler.runOnce(100);

        assertThat(done).isZero();
        assertOrder("o_future_1", "issued", null);
    }

    @Test
    void runOnce_redeemedOrderAfterShowEnd_shouldStayRedeemed() {
        OffsetDateTime now = OffsetDateTime.now(CST);
        insertShow("s_redeemed_1", now.minusHours(3), now.minusHours(1));
        insertOrder("o_redeemed_1", "s_redeemed_1", now.minusHours(2), "redeemed");

        int done = scheduler.runOnce(100);

        assertThat(done).isZero();
        assertOrder("o_redeemed_1", "redeemed", null);
    }

    @Test
    void runOnce_issuedOrderWithoutShow_shouldNotBeTouched() {
        OffsetDateTime now = OffsetDateTime.now(CST);
        // 不建 show_schedule 行：JOIN 匹配不到，订单保持 issued（已知边界，不误伤）
        insertOrder("o_orphan_1", "s_missing_1", now.minusHours(2), "issued");

        int done = scheduler.runOnce(100);

        assertThat(done).isZero();
        assertOrder("o_orphan_1", "issued", null);
    }

    /** 插入场次：结束时间入参控制是否已结束 */
    private void insertShow(String showId, OffsetDateTime start, OffsetDateTime end) {
        OffsetDateTime now = OffsetDateTime.now(CST);
        jdbcTemplate.update(
                "INSERT INTO show_schedule(show_id, movie_id, cinema_id, hall_id, seat_map_id, "
                        + "start_time, end_time, price, status, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                showId, "m_test", "c_test", "h_test", "sm_test",
                start, end, new BigDecimal("50.00"), "on_sale", now, now);
    }

    /** 插入订单：status 入参控制目标状态，seat_price_snapshot 必填给空快照 */
    private void insertOrder(String orderId, String showId, OffsetDateTime start, String status) {
        OffsetDateTime now = OffsetDateTime.now(CST);
        jdbcTemplate.update(
                "INSERT INTO order_ticket(order_id, user_id, show_id, lock_id, movie_title, cinema_name, hall_name, "
                        + "start_time, seat_ids_json, unit_price, amount, seat_price_snapshot, status, "
                        + "ticket_code, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                orderId, "u_test", showId, "lk_" + orderId, "测试片", "测试影城", "1号厅", start,
                "[\"sm_test:1:1\"]", new BigDecimal("50.00"), new BigDecimal("50.00"), "[]", status,
                "TKT-20260101-0001", now, now);
    }

    /** 断言订单状态与取消原因 */
    private void assertOrder(String orderId, String expectedStatus, String expectedReason) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM order_ticket WHERE order_id = ?", String.class, orderId);
        assertThat(status).isEqualTo(expectedStatus);
        String reason = jdbcTemplate.queryForObject(
                "SELECT cancel_reason FROM order_ticket WHERE order_id = ?", String.class, orderId);
        assertThat(reason).isEqualTo(expectedReason);
    }
}
