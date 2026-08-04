package com.cinepass.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C 端座位库存集成测试：seat-map 懒播种、锁座、冲突、情侣座规则、解锁。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SeatLockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;
    private String userId;

    @BeforeEach
    void setUp() throws Exception {
        String nickname = "lk_user_" + System.nanoTime();
        String phone = "138" + String.format("%08d", System.nanoTime() % 100000000L);
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(register.getResponse().getContentAsString()).path("data");
        token = data.path("accessToken").asText();
        userId = data.path("userId").asText();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(8));
        OffsetDateTime start = now.plusHours(3);
        OffsetDateTime end = start.plusHours(2);
        seedCatalog(now, start, end);
    }

    @Test
    void seatMap_shouldLazySeedAndReturnSparseSeats() throws Exception {
        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM seat_status WHERE show_id = ?", Integer.class, "s_lk_1");
        assertThat(before).isZero();

        mockMvc.perform(get("/api/v1/shows/s_lk_1/seat-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.showId").value("s_lk_1"))
                .andExpect(jsonPath("$.data.seatMapId").value("sm_lk"))
                .andExpect(jsonPath("$.data.rows").value(3))
                .andExpect(jsonPath("$.data.cols").value(4))
                .andExpect(jsonPath("$.data.screenLabel").value("银幕"))
                .andExpect(jsonPath("$.data.price").value(55.0))
                .andExpect(jsonPath("$.data.legend.available").value("可选"))
                .andExpect(jsonPath("$.data.seats.length()").value(5))
                .andExpect(jsonPath("$.data.seats[0].seatId").exists())
                .andExpect(jsonPath("$.data.seats[0].status").value("available"));

        Integer after = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM seat_status WHERE show_id = ?", Integer.class, "s_lk_1");
        assertThat(after).isEqualTo(5);
    }

    @Test
    void lock_successThenSecondTaken_thenUnlock() throws Exception {
        mockMvc.perform(get("/api/v1/shows/s_lk_1/seat-map")).andExpect(status().isOk());

        MvcResult locked = mockMvc.perform(post("/api/v1/locks")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "idem_lk_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":\"s_lk_1\",\"seatIds\":[\"sm_lk:1:1\",\"sm_lk:1:2\"],\"ttlSeconds\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.lockId").value(org.hamcrest.Matchers.startsWith("lk")))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.seatIds.length()").value(2))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andReturn();

        String lockId = objectMapper.readTree(locked.getResponse().getContentAsString())
                .path("data").path("lockId").asText();

        mockMvc.perform(get("/api/v1/locks/" + lockId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockId").value(lockId));

        mockMvc.perform(post("/api/v1/locks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":\"s_lk_1\",\"seatIds\":[\"sm_lk:1:1\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.data.errorCode").value("SEAT_TAKEN"))
                .andExpect(jsonPath("$.data.conflictSeatIds[0]").value("sm_lk:1:1"))
                .andExpect(jsonPath("$.data.showId").value("s_lk_1"));

        mockMvc.perform(delete("/api/v1/locks/" + lockId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockId").value(lockId))
                .andExpect(jsonPath("$.data.released").value(true));

        mockMvc.perform(delete("/api/v1/locks/" + lockId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.released").value(true));

        mockMvc.perform(post("/api/v1/locks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":\"s_lk_1\",\"seatIds\":[\"sm_lk:1:1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    void lock_coupleRule_shouldRejectPartialPair() throws Exception {
        mockMvc.perform(get("/api/v1/shows/s_lk_1/seat-map")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/locks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":\"s_lk_1\",\"seatIds\":[\"sm_lk:2:1\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.data.errorCode").value("COUPLE_RULE"));

        mockMvc.perform(post("/api/v1/locks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":\"s_lk_1\",\"seatIds\":[\"sm_lk:2:1\",\"sm_lk:2:2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatIds.length()").value(2));
    }

    @Test
    void recoSeats_shouldReturnPlans() throws Exception {
        mockMvc.perform(get("/api/v1/shows/s_lk_1/seat-map")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reco/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":\"s_lk_1\",\"count\":2,\"preferRow\":\"middle\",\"preferSide\":\"center\",\"together\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.showId").value("s_lk_1"))
                .andExpect(jsonPath("$.data.plans").isArray())
                .andExpect(jsonPath("$.data.plans.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.plans[0].seatIds.length()").value(2));
    }

    private void seedCatalog(OffsetDateTime now, OffsetDateTime start, OffsetDateTime end) {
        jdbcTemplate.update("DELETE FROM seat_status WHERE show_id = ?", "s_lk_1");
        jdbcTemplate.update("DELETE FROM seat_lock WHERE show_id = ?", "s_lk_1");
        jdbcTemplate.update("DELETE FROM order_ticket WHERE show_id = ?", "s_lk_1");
        jdbcTemplate.update("DELETE FROM show_zone_price WHERE show_id = ?", "s_lk_1");
        jdbcTemplate.update("DELETE FROM show_schedule WHERE show_id = ?", "s_lk_1");
        jdbcTemplate.update("DELETE FROM seat WHERE seat_map_id = ?", "sm_lk");
        jdbcTemplate.update("DELETE FROM hall WHERE hall_id = ?", "h_lk_1");
        jdbcTemplate.update("DELETE FROM seat_map WHERE seat_map_id = ?", "sm_lk");
        jdbcTemplate.update("DELETE FROM cinema WHERE cinema_id = ?", "c_lk_1");
        jdbcTemplate.update("DELETE FROM movie WHERE movie_id = ?", "m_lk_1");

        jdbcTemplate.update(
                "INSERT INTO movie(movie_id, title, poster_url, genres_json, rating, duration_min, "
                        + "release_date, status, description, cast_text, want_see_count, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "m_lk_1", "锁座测试片", "http://example.com/p.jpg", "[\"剧情\"]", 8.0,
                120, LocalDate.of(2026, 1, 1), "hot_showing", "desc", "cast", 0, now, now);
        jdbcTemplate.update(
                "INSERT INTO cinema(cinema_id, city_id, city_name, name, address, lat, lng, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                "c_lk_1", "city_sh", "上海市", "锁座影城", "地址", 31.2, 121.5, now, now);
        jdbcTemplate.update(
                "INSERT INTO seat_map(seat_map_id, cinema_id, rows_n, cols_n, screen_label, mutable) "
                        + "VALUES (?,?,?,?,?,?)",
                "sm_lk", "c_lk_1", 3, 4, "银幕", true);
        jdbcTemplate.update(
                "INSERT INTO hall(hall_id, cinema_id, name, seat_map_id) VALUES (?,?,?,?)",
                "h_lk_1", "c_lk_1", "1号厅", "sm_lk");
        jdbcTemplate.update(
                "INSERT INTO seat(seat_id, seat_map_id, graph_row, graph_col, row_no, col_no, "
                        + "seat_name, seat_type, zone, couple_pair_id, default_status) VALUES "
                        + "(?,?,?,?,?,?,?,?,?,?,?), (?,?,?,?,?,?,?,?,?,?,?), (?,?,?,?,?,?,?,?,?,?,?), "
                        + "(?,?,?,?,?,?,?,?,?,?,?), (?,?,?,?,?,?,?,?,?,?,?)",
                "sm_lk:1:1", "sm_lk", 1, 1, 1, 1, "1排1座", "normal", "normal", null, "available",
                "sm_lk:1:2", "sm_lk", 1, 2, 1, 2, "1排2座", "normal", "normal", null, "available",
                "sm_lk:1:3", "sm_lk", 1, 3, 1, 3, "1排3座", "normal", "golden", null, "available",
                "sm_lk:2:1", "sm_lk", 2, 1, 2, 1, "2排1座", "couple", "normal", "cp_lk_1", "available",
                "sm_lk:2:2", "sm_lk", 2, 2, 2, 2, "2排2座", "couple", "normal", "cp_lk_1", "available");
        jdbcTemplate.update(
                "INSERT INTO show_schedule(show_id, movie_id, cinema_id, hall_id, seat_map_id, "
                        + "start_time, end_time, price, status, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                "s_lk_1", "m_lk_1", "c_lk_1", "h_lk_1", "sm_lk",
                start, end, new BigDecimal("55.00"), "on_sale", now, now);
        jdbcTemplate.update(
                "INSERT INTO show_zone_price(show_id, zone, price) VALUES (?,?,?), (?,?,?)",
                "s_lk_1", "normal", new BigDecimal("55.00"),
                "s_lk_1", "golden", new BigDecimal("65.00"));
    }
}
