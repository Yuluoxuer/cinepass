package com.cinepass.controller;

import com.cinepass.mapper.OrderTicketMapper;
import com.cinepass.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单管理集成测试：创建 / 幂等 / 列表 / 详情 / 取消释放锁座。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderTicketMapper orderTicketMapper;

    private String userId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        ensureAccountViaRegister("运营小王", "13900000002", "demo123456", "staff");
        ensureAccountViaRegister("系统管理员", "13900000001", "demo123456", "admin");

        String nickname = "ord_user_" + System.nanoTime();
        String phone = "139" + String.format("%08d", System.nanoTime() % 100000000L);
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(register.getResponse().getContentAsString()).path("data");
        token = data.path("accessToken").asText();
        userId = data.path("userId").asText();
        assertThat(userId).matches("^u[0-9a-f]{31}$");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(8));
        OffsetDateTime start = now.plusHours(2);
        OffsetDateTime end = start.plusHours(2);
        OffsetDateTime expire = now.plusMinutes(15);

        ensureCatalog(now, start, end);
        jdbcTemplate.update(
                "DELETE FROM seat_status WHERE show_id = ?", "s_ord_1");
        jdbcTemplate.update("DELETE FROM seat_lock WHERE lock_id = ?", "lk_ord_1");
        jdbcTemplate.update("DELETE FROM order_ticket WHERE lock_id = ?", "lk_ord_1");

        jdbcTemplate.update(
                "INSERT INTO seat_status(show_id, seat_id, status, lock_id, user_id, expire_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?), (?,?,?,?,?,?,?)",
                "s_ord_1", "sm_ord:1:1", "locked", "lk_ord_1", userId, expire, now,
                "s_ord_1", "sm_ord:1:2", "locked", "lk_ord_1", userId, expire, now);
        jdbcTemplate.update(
                "INSERT INTO seat_lock(lock_id, show_id, user_id, seat_ids_json, status, ttl_seconds, "
                        + "expire_at, session_id, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                "lk_ord_1", "s_ord_1", userId, "[\"sm_ord:1:1\",\"sm_ord:1:2\"]",
                "active", 900, expire, "sess_ord", now, now);
    }

    private void ensureAccountViaRegister(String nickname, String phone, String password, String role)
            throws Exception {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM user_account WHERE nickname = ?", Integer.class, nickname);
        if (exists == null || exists == 0) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"" + nickname + "\",\"phone\":\"" + phone
                                    + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk());
        }
        if (!"user".equals(role)) {
            jdbcTemplate.update("UPDATE user_account SET role = ?, cinema_id = NULL WHERE nickname = ?",
                    role, nickname);
        }
    }

    private void ensureCatalog(OffsetDateTime now, OffsetDateTime start, OffsetDateTime end) {
        Integer movies = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM movie WHERE movie_id = ?", Integer.class, "m_ord_1");
        if (movies != null && movies == 0) {
            jdbcTemplate.update(
                    "INSERT INTO movie(movie_id, title, poster_url, genres_json, rating, duration_min, "
                            + "release_date, status, description, cast_text, want_see_count, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    "m_ord_1", "订单测试片", "http://example.com/p.jpg", "[\"剧情\"]", 8.0,
                    120, LocalDate.of(2026, 1, 1), "hot_showing", "desc", "cast", 0, now, now);
        }
        Integer cinemas = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM cinema WHERE cinema_id = ?", Integer.class, "c_ord_1");
        if (cinemas != null && cinemas == 0) {
            jdbcTemplate.update(
                    "INSERT INTO cinema(cinema_id, city_id, city_name, name, address, lat, lng, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?)",
                    "c_ord_1", "city_sh", "上海市", "测试影城", "地址1", 31.2, 121.5, now, now);
            jdbcTemplate.update(
                    "INSERT INTO seat_map(seat_map_id, name, cinema_id, rows_n, cols_n, screen_label, mutable) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    "sm_ord", "座位图", "c_ord_1", 5, 5, "银幕", true);
            jdbcTemplate.update(
                    "INSERT INTO hall(hall_id, cinema_id, name, seat_map_id) "
                            + "VALUES (?,?,?,?)",
                    "h_ord_1", "c_ord_1", "1号厅", "sm_ord");
            jdbcTemplate.update(
                    "INSERT INTO seat(seat_id, seat_map_id, graph_row, graph_col, row_no, col_no, "
                            + "seat_name, seat_type, zone, couple_pair_id, default_status) VALUES "
                            + "(?,?,?,?,?,?,?,?,?,?,?), (?,?,?,?,?,?,?,?,?,?,?)",
                    "sm_ord:1:1", "sm_ord", 1, 1, 1, 1, "1排1座", "normal", "normal", null, "available",
                    "sm_ord:1:2", "sm_ord", 1, 2, 1, 2, "1排2座", "normal", "normal", null, "available");
            jdbcTemplate.update(
                    "INSERT INTO show_schedule(show_id, movie_id, cinema_id, hall_id, seat_map_id, "
                            + "start_time, end_time, price, status, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    "s_ord_1", "m_ord_1", "c_ord_1", "h_ord_1", "sm_ord",
                    start, end, new BigDecimal("55.00"), "on_sale", now, now);
            jdbcTemplate.update(
                    "INSERT INTO show_zone_price(show_id, zone, price) VALUES (?,?,?)",
                    "s_ord_1", "normal", new BigDecimal("55.00"));
        }
    }

    @Test
    void createListGetCancel_shouldWorkAndReleaseSeats() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\",\"sessionId\":\"sess_ord\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("pending_pay"))
                .andExpect(jsonPath("$.data.amount").value(110.0))
                .andExpect(jsonPath("$.data.orderId").value(org.hamcrest.Matchers.matchesPattern("^o[0-9a-f]{32}$")))
                .andReturn();

        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();

        // 同 lock 幂等
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId));

        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].orderId").value(orderId));

        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockId").value("lk_ord_1"));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"user_cancel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));

        String lockStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM seat_lock WHERE lock_id = ?", String.class, "lk_ord_1");
        assertThat(lockStatus).isEqualTo("released");
        Integer locked = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM seat_status WHERE lock_id = ? AND status = 'locked'",
                Integer.class, "lk_ord_1");
        assertThat(locked).isZero();
    }

    @Test
    void create_withoutToken_shouldUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_blankLockId_shouldParamError() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void create_unknownLock_shouldLockExpired() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_missing\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4101));
    }

    @Test
    void create_lockOwnedByOther_shouldForbidden() throws Exception {
        String otherToken = registerUser();
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void create_expiredLock_shouldLockExpired() throws Exception {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1);
        jdbcTemplate.update("UPDATE seat_lock SET expire_at = ?, status = 'active' WHERE lock_id = ?",
                past, "lk_ord_1");
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4101));
    }

    @Test
    void create_releasedLock_shouldLockExpired() throws Exception {
        jdbcTemplate.update("UPDATE seat_lock SET status = 'released' WHERE lock_id = ?", "lk_ord_1");
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4101));
    }

    @Test
    void getAndCancel_crossUser_shouldForbidden() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();

        String otherToken = registerUser();
        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void get_missingOrder_shouldNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/o00000000000000000000000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void cancel_missingOrder_shouldNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/orders/o00000000000000000000000000000000/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void cancel_twice_shouldIdempotent() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"user_cancel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));
    }

    @Test
    void cancel_issuedOrder_shouldNotCancellable() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();
        jdbcTemplate.update("UPDATE order_ticket SET status = 'issued' WHERE order_id = ?", orderId);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4102));
    }

    @Test
    void list_illegalStatus_shouldParamError() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("status", "paid")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void list_pageBoundaries_shouldClamp() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("page", "0")
                        .param("size", "999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    void adminOrders_userForbidden_staffWithoutCinemaForbidden_adminOk() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // staff 未绑定影院：通过 @Staff 后业务层拒绝
        String staffToken = loginAs("运营小王", "demo123456");
        mockMvc.perform(get("/api/v1/admin/orders").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40301));

        String adminToken = loginAs("系统管理员", "demo123456");
        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("status", "pending_pay")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void adminOrders_staffScopedByCinema_shouldOnlySeeOwnCinema() throws Exception {
        // 先下单（本影院 c_ord_1）
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();

        String adminToken = loginAs("系统管理员", "demo123456");
        String sameNick = "staff_same_" + System.nanoTime();
        String otherNick = "staff_other_" + System.nanoTime();
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + sameNick
                                + "\",\"password\":\"StaffPass1\",\"role\":\"staff\",\"cinemaId\":\"c_ord_1\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + otherNick
                                + "\",\"password\":\"StaffPass1\",\"role\":\"staff\",\"cinemaId\":\"c_other\"}"))
                .andExpect(status().isOk());

        String staffSame = loginAs(sameNick, "StaffPass1");
        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("userId", userId)
                        .header("Authorization", "Bearer " + staffSame))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].orderId").value(orderId));

        String staffOther = loginAs(otherNick, "StaffPass1");
        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("userId", userId)
                        .header("Authorization", "Bearer " + staffOther))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void adminOrders_adminFilterByStatusAndDate_shouldWork() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk());

        String adminToken = loginAs("系统管理员", "demo123456");
        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("userId", userId)
                        .param("status", "pending_pay")
                        .param("dateFrom", today.minusDays(1).toString())
                        .param("dateTo", today.plusDays(1).toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("userId", userId)
                        .param("status", "cancelled")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void listMine_filterByStatus_shouldMatch() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();

        mockMvc.perform(get("/api/v1/orders").param("status", "pending_pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].orderId").value(orderId));

        mockMvc.perform(get("/api/v1/orders").param("status", "cancelled")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void cancel_nullDto_shouldDefaultReason() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));

        String reason = jdbcTemplate.queryForObject(
                "SELECT cancel_reason FROM order_ticket WHERE order_id = ?", String.class, orderId);
        assertThat(reason).isEqualTo("user_cancel");
    }

    @Test
    void adminOrders_withoutToken_shouldUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void payQrcode_ownPendingOrder_shouldReturnPayQr() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/pay-qrcode")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.amount").value(110.0))
                .andExpect(jsonPath("$.data.expireAt").isNotEmpty())
                .andExpect(jsonPath("$.data.payUrl",
                        org.hamcrest.Matchers.containsString("/m/pay/" + orderId + "?t=")))
                .andExpect(jsonPath("$.data.pollIntervalMs").value(2000));
    }

    @Test
    void payQrcode_otherUser_shouldForbidden() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();

        String otherToken = registerUser();
        mockMvc.perform(get("/api/v1/orders/" + orderId + "/pay-qrcode")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void payQrcode_issuedOrder_shouldNotPayable() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();
        jdbcTemplate.update("UPDATE order_ticket SET status = 'issued' WHERE order_id = ?", orderId);

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/pay-qrcode")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4103));
    }

    @Test
    void payQrcode_expiredOrder_shouldLockExpired() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1);
        jdbcTemplate.update("UPDATE order_ticket SET expire_at = ? WHERE order_id = ?", past, orderId);

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/pay-qrcode")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4101));
    }

    @Test
    void payQrcode_missingOrder_shouldNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/o00000000000000000000000000000000/pay-qrcode")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void payQrcode_withoutToken_shouldUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders/o00000000000000000000000000000000/pay-qrcode"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paySession_validToken_shouldReturnSummary() throws Exception {
        String orderId = createOrder();
        String payToken = payTokenOf(orderId);

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/pay-session")
                        .param("t", payToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.amount").value(110.0))
                .andExpect(jsonPath("$.data.movieTitle").value("订单测试片"))
                .andExpect(jsonPath("$.data.cinemaName").value("测试影城"))
                .andExpect(jsonPath("$.data.hallName").value("1号厅"))
                .andExpect(jsonPath("$.data.seatIds.length()").value(2))
                .andExpect(jsonPath("$.data.seatNames[0]").value("1排1座"))
                .andExpect(jsonPath("$.data.status").value("pending_pay"))
                .andExpect(jsonPath("$.data.ticketCode").doesNotExist());
    }

    @Test
    void paySession_invalidToken_shouldPayTokenInvalid() throws Exception {
        String orderId = createOrder();
        mockMvc.perform(get("/api/v1/orders/" + orderId + "/pay-session")
                        .param("t", "garbage-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4105));
    }

    @Test
    void paySession_tokenMismatchOrder_shouldPayTokenInvalid() throws Exception {
        String orderId = createOrder();
        String payToken = payTokenOf(orderId);
        // 用订单 A 的 token 拉订单 B 的摘要
        mockMvc.perform(get("/api/v1/orders/o00000000000000000000000000000000/pay-session")
                        .param("t", payToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4105));
    }

    @Test
    void pay_ownJwt_shouldIssueAndConsumeSeats() throws Exception {
        String orderId = createOrder();

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"mobile_qr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("issued"))
                .andExpect(jsonPath("$.data.ticketCode",
                        org.hamcrest.Matchers.matchesPattern("^TKT-\\d{8}-[0-9a-f]{4}$")))
                .andExpect(jsonPath("$.data.payChannel").value("mobile_qr"))
                .andExpect(jsonPath("$.data.payAt").isNotEmpty())
                .andExpect(jsonPath("$.data.expireAt").doesNotExist());

        // 座位最终落定 sold、锁座 consumed
        String lockStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM seat_lock WHERE lock_id = ?", String.class, "lk_ord_1");
        assertThat(lockStatus).isEqualTo("consumed");
        Integer sold = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM seat_status WHERE lock_id = ? AND status = 'sold'",
                Integer.class, "lk_ord_1");
        assertThat(sold).isEqualTo(2);
    }

    @Test
    void pay_xPayToken_shouldIssue() throws Exception {
        String orderId = createOrder();
        String payToken = payTokenOf(orderId);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/pay")
                        .header("X-Pay-Token", payToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"mobile_qr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("issued"));
    }

    @Test
    void pay_alreadyIssued_shouldIdempotentReturnSameTicket() throws Exception {
        String orderId = createOrder();
        String ticketCode = payAndGetTicketCode(orderId);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("issued"))
                .andExpect(jsonPath("$.data.ticketCode").value(ticketCode));
    }

    @Test
    void pay_expiredOrder_shouldLockExpiredEvenWithValidToken() throws Exception {
        String orderId = createOrder();
        String payToken = payTokenOf(orderId);
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1);
        jdbcTemplate.update("UPDATE order_ticket SET expire_at = ? WHERE order_id = ?", past, orderId);

        // 二维码/token 仍有效，但 DB 权威截止已过，必须拒绝支付
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/pay")
                        .header("X-Pay-Token", payToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4101));
    }

    @Test
    void pay_otherUserJwt_shouldForbidden() throws Exception {
        String orderId = createOrder();
        String otherToken = registerUser();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void pay_cancelledOrder_shouldNotPayable() throws Exception {
        String orderId = createOrder();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"user_cancel\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4103));
    }

    @Test
    void pay_withoutAnyAuth_shouldUnauthorized() throws Exception {
        String orderId = createOrder();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void redeemQrcode_ownIssued_shouldReturnRedeemUrl() throws Exception {
        String orderId = createOrder();
        payAndGetTicketCode(orderId);

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/redeem-qrcode")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.ticketCode",
                        org.hamcrest.Matchers.matchesPattern("^TKT-\\d{8}-[0-9a-f]{4}$")))
                .andExpect(jsonPath("$.data.redeemUrl",
                        org.hamcrest.Matchers.containsString("/m/redeem/" + orderId + "?t=")));
    }

    @Test
    void redeemQrcode_notIssued_shouldNotRedeemable() throws Exception {
        String orderId = createOrder();
        mockMvc.perform(get("/api/v1/orders/" + orderId + "/redeem-qrcode")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4104));
    }

    @Test
    void redeemSession_validToken_shouldReturnSummary() throws Exception {
        String orderId = createOrder();
        payAndGetTicketCode(orderId);
        String redeemToken = redeemTokenOf(orderId);

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/redeem-session")
                        .param("t", redeemToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("issued"))
                .andExpect(jsonPath("$.data.ticketCode",
                        org.hamcrest.Matchers.matchesPattern("^TKT-\\d{8}-[0-9a-f]{4}$")))
                .andExpect(jsonPath("$.data.seatNames[0]").value("1排1座"));
    }

    @Test
    void redeem_ownJwt_shouldRedeemed() throws Exception {
        String orderId = createOrder();
        payAndGetTicketCode(orderId);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/redeem")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("redeemed"));
    }

    @Test
    void redeem_xRedeemToken_shouldRedeemed() throws Exception {
        String orderId = createOrder();
        payAndGetTicketCode(orderId);
        String redeemToken = redeemTokenOf(orderId);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/redeem")
                        .header("X-Redeem-Token", redeemToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("redeemed"));
    }

    @Test
    void redeem_notIssued_shouldNotRedeemable() throws Exception {
        String orderId = createOrder();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/redeem")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4104));
    }

    @Test
    void redeem_redeemedTwice_shouldIdempotent() throws Exception {
        String orderId = createOrder();
        payAndGetTicketCode(orderId);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/redeem")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("redeemed"));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/redeem")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("redeemed"));
    }

    @Test
    void expireOrder_pastDue_shouldExpireOrderReleaseLockAndSeats() throws Exception {
        String orderId = createOrder();
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1);
        jdbcTemplate.update("UPDATE order_ticket SET expire_at = ? WHERE order_id = ?", past, orderId);

        // 清扫候选应命中该订单
        List<String> candidates = orderTicketMapper.selectExpiredPendingPay(
                OffsetDateTime.now(ZoneOffset.ofHours(8)), 100);
        assertThat(candidates).contains(orderId);

        orderService.expireOrder(orderId);

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM order_ticket WHERE order_id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("expired");
        String lockStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM seat_lock WHERE lock_id = ?", String.class, "lk_ord_1");
        assertThat(lockStatus).isEqualTo("expired");
        // 释放时 lock_id 被置 NULL，故按场次统计可用座位
        Integer available = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM seat_status WHERE show_id = ? AND status = 'available'",
                Integer.class, "s_ord_1");
        assertThat(available).isEqualTo(2);
    }

    @Test
    void expireOrder_notPastDue_shouldBeNoop() throws Exception {
        String orderId = createOrder();
        // 默认 expire_at = now + 15min，未到期不应被清扫
        orderService.expireOrder(orderId);

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM order_ticket WHERE order_id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("pending_pay");
        String lockStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM seat_lock WHERE lock_id = ?", String.class, "lk_ord_1");
        assertThat(lockStatus).isEqualTo("active");
    }

    @Test
    void expireOrder_afterPaid_shouldNotTouchIssuedOrder() throws Exception {
        String orderId = createOrder();
        payAndGetTicketCode(orderId);
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1);
        jdbcTemplate.update("UPDATE order_ticket SET expire_at = ? WHERE order_id = ?", past, orderId);

        orderService.expireOrder(orderId);

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM order_ticket WHERE order_id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("issued");
        String lockStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM seat_lock WHERE lock_id = ?", String.class, "lk_ord_1");
        assertThat(lockStatus).isEqualTo("consumed");
    }

    @Test
    void pay_expiredByScheduler_shouldLockExpired() throws Exception {
        String orderId = createOrder();
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1);
        jdbcTemplate.update("UPDATE order_ticket SET expire_at = ? WHERE order_id = ?", past, orderId);
        orderService.expireOrder(orderId);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4101));
    }

    private String createOrder() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockId\":\"lk_ord_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asText();
    }

    private String payTokenOf(String orderId) throws Exception {
        MvcResult qr = mockMvc.perform(get("/api/v1/orders/" + orderId + "/pay-qrcode")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String payUrl = objectMapper.readTree(qr.getResponse().getContentAsString())
                .path("data").path("payUrl").asText();
        return payUrl.substring(payUrl.indexOf("t=") + 2);
    }

    private String redeemTokenOf(String orderId) throws Exception {
        MvcResult qr = mockMvc.perform(get("/api/v1/orders/" + orderId + "/redeem-qrcode")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String redeemUrl = objectMapper.readTree(qr.getResponse().getContentAsString())
                .path("data").path("redeemUrl").asText();
        return redeemUrl.substring(redeemUrl.indexOf("t=") + 2);
    }

    private String payAndGetTicketCode(String orderId) throws Exception {
        MvcResult paid = mockMvc.perform(post("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(paid.getResponse().getContentAsString())
                .path("data").path("ticketCode").asText();
    }

    private String registerUser() throws Exception {
        String nickname = "ord_other_" + System.nanoTime();
        String phone = "136" + String.format("%08d", System.nanoTime() % 100000000L);
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(register.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String loginAs(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
