package com.cinepass.controller;

import com.cinepass.service.EsIndexService;
import com.cinepass.service.EsSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 推荐模块集成测试：每周热门排序/limit/heatTag/点击影响、个人推荐回退与个性化、影片详情点击埋点。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private EsSearchService esSearchService;

    @MockBean
    private EsIndexService esIndexService;

    @BeforeEach
    void setUp() throws Exception {
        // 基础影片集：排序期望 a > b > d > c > soon（a 5 单、b 2 单，其余 0，无点击）
        insertMovie("m_r_a", "剧情片A", "[\"剧情\"]", 8.0, "hot_showing", LocalDate.of(2026, 7, 1));
        insertMovie("m_r_b", "喜剧片B", "[\"喜剧\"]", 7.0, "hot_showing", LocalDate.of(2026, 6, 1));
        insertMovie("m_r_c", "动作片C", "[\"动作\"]", 6.0, "hot_showing", LocalDate.of(2026, 5, 1));
        insertMovie("m_r_d", "剧情片D", "[\"剧情\"]", 8.8, "hot_showing", LocalDate.of(2026, 7, 15));
        insertMovie("m_soon", "科幻片E", "[\"科幻\"]", null, "coming_soon", LocalDate.of(2026, 9, 1));
        insertShow("s_a", "m_r_a");
        insertShow("s_b", "m_r_b");
        insertIssuedOrder("o_a1", "u_any", "s_a", "剧情片A");
        insertIssuedOrder("o_a2", "u_any", "s_a", "剧情片A");
        insertIssuedOrder("o_a3", "u_any", "s_a", "剧情片A");
        insertIssuedOrder("o_a4", "u_any", "s_a", "剧情片A");
        insertIssuedOrder("o_a5", "u_any", "s_a", "剧情片A");
        insertIssuedOrder("o_b1", "u_any", "s_b", "喜剧片B");
        insertIssuedOrder("o_b2", "u_any", "s_b", "喜剧片B");
        ensureAccountViaRegister("演示用户甲", "13800000001", "demo123456");
    }

    /** 经真实注册接口建号（u+uuid7），幂等；不写死假 user_id */
    private void ensureAccountViaRegister(String nickname, String phone, String password) throws Exception {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM user_account WHERE nickname = ?", Integer.class, nickname);
        if (exists == null || exists == 0) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"" + nickname + "\",\"phone\":\"" + phone
                                    + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(org.hamcrest.Matchers.matchesPattern("^u[0-9a-f]{31}$")));
        }
    }

    @Test
    void weeklyHotRanksByOrdersThenRatingAndHonorsLimit() throws Exception {
        mockMvc.perform(get("/api/v1/reco/weekly-hot").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.computedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].movie.movieId").value("m_r_a"))
                .andExpect(jsonPath("$.data.items[0].heatTag").value("本周爆款"))
                .andExpect(jsonPath("$.data.items[1].movie.movieId").value("m_r_b"))
                .andExpect(jsonPath("$.data.items[1].heatTag").value("人气佳作"));
    }

    @Test
    void weeklyHotClicksLiftMovieRank() throws Exception {
        // m_r_d 近 7 天 30 次点击，超过无点击的 m_r_b（2 单）
        insertClick("m_r_d", 30);
        mockMvc.perform(get("/api/v1/reco/weekly-hot").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].movie.movieId").value("m_r_a"))
                .andExpect(jsonPath("$.data.items[1].movie.movieId").value("m_r_d"))
                .andExpect(jsonPath("$.data.items[2].movie.movieId").value("m_r_b"));
    }

    @Test
    void weeklyHotClampsLimitTo20() throws Exception {
        mockMvc.perform(get("/api/v1/reco/weekly-hot").param("limit", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(5));
    }

    @Test
    void personalWithoutLoginFallsBackToHotList() throws Exception {
        mockMvc.perform(get("/api/v1/reco/personal").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mode").value("fallback_hot"))
                .andExpect(jsonPath("$.data.items[0].movie.movieId").value("m_r_a"))
                .andExpect(jsonPath("$.data.items[0].reason").value("本周热门"));
    }

    @Test
    void personalLoggedInScoresByGenreAndKeepsSameGenreRunAtMost3() throws Exception {
        // 登录用户偏好喜剧；另加 5 部高分喜剧 + 1 部剧情，验证类型匹配优先、同类型连续 ≤3
        insertMovie("m_com1", "喜剧1", "[\"喜剧\"]", 9.5, "hot_showing", LocalDate.of(2026, 7, 20));
        insertMovie("m_com2", "喜剧2", "[\"喜剧\"]", 9.4, "hot_showing", LocalDate.of(2026, 7, 20));
        insertMovie("m_com3", "喜剧3", "[\"喜剧\"]", 9.3, "hot_showing", LocalDate.of(2026, 7, 20));
        insertMovie("m_com4", "喜剧4", "[\"喜剧\"]", 9.2, "hot_showing", LocalDate.of(2026, 7, 20));
        insertMovie("m_com5", "喜剧5", "[\"喜剧\"]", 9.1, "hot_showing", LocalDate.of(2026, 7, 20));
        insertMovie("m_dra1", "剧情1", "[\"剧情\"]", 7.0, "hot_showing", LocalDate.of(2026, 7, 20));
        String token = bearer(loginAs("演示用户甲", "demo123456"));
        setUserProfile("演示用户甲", "[\"喜剧\"]");

        MvcResult result = mockMvc.perform(get("/api/v1/reco/personal")
                        .param("limit", "8")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("personalized"))
                .andExpect(jsonPath("$.data.items.length()").value(8))
                .andExpect(jsonPath("$.data.items[0].movie.genres[0]").value("喜剧"))
                .andExpect(jsonPath("$.data.items[1].movie.genres[0]").value("喜剧"))
                .andExpect(jsonPath("$.data.items[2].movie.genres[0]").value("喜剧"))
                .andExpect(jsonPath("$.data.items[3].movie.movieId").value("m_r_a"))
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("items");
        // 校验同类型连续不超过 3：第 4 个必须是不同主类型，且连续段内主类型不重复 4 次
        String[] genres = new String[8];
        for (int i = 0; i < items.size(); i++) {
            genres[i] = items.get(i).path("movie").path("genres").get(0).asText();
        }
        int run = 1;
        for (int i = 1; i < genres.length; i++) {
            run = genres[i].equals(genres[i - 1]) ? run + 1 : 1;
            assertThat(run).isLessThanOrEqualTo(3);
        }
    }

    @Test
    void personalExcludeMovieIdsSkipsMovies() throws Exception {
        String token = bearer(loginAs("演示用户甲", "demo123456"));
        setUserProfile("演示用户甲", "[\"剧情\"]");

        mockMvc.perform(get("/api/v1/reco/personal")
                        .param("limit", "5")
                        .param("excludeMovieIds", "m_r_a")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("personalized"))
                .andExpect(jsonPath("$.data.items[?(@.movie.movieId=='m_r_a')]").isEmpty());
    }

    @Test
    void movieDetailIncrementsClickCounter() throws Exception {
        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/v1/movies/m_r_a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT cnt FROM reco_clicks WHERE movie_id = ? AND click_date = ?", Integer.class,
                "m_r_a", today);
        assertThat(cnt).isEqualTo(1);

        mockMvc.perform(get("/api/v1/movies/m_r_a"))
                .andExpect(status().isOk());
        cnt = jdbcTemplate.queryForObject(
                "SELECT cnt FROM reco_clicks WHERE movie_id = ? AND click_date = ?", Integer.class,
                "m_r_a", today);
        assertThat(cnt).isEqualTo(2);
    }

    private void insertMovie(String movieId, String title, String genresJson, Double rating,
                             String status, LocalDate releaseDate) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO movie(movie_id, title, poster_url, genres_json, rating, duration_min, "
                        + "release_date, status, description, cast_text, want_see_count, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                movieId, title, "http://example.com/" + movieId + ".jpg", genresJson, rating,
                120, releaseDate, status, "desc", "cast", 0, now, now);
    }

    private void insertShow(String showId, String movieId) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO show_schedule(show_id, movie_id, cinema_id, hall_id, seat_map_id, "
                        + "start_time, end_time, price, status, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                showId, movieId, "c_any", "h_any", "sm_any",
                now, now.plusHours(2), 50.00, "on_sale", now, now);
    }

    private void insertIssuedOrder(String orderId, String userId, String showId, String movieTitle) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO order_ticket(order_id, user_id, show_id, lock_id, movie_title, cinema_name, hall_name, "
                        + "start_time, seat_ids_json, unit_price, amount, seat_price_snapshot, status, "
                        + "ticket_code, pay_channel, pay_at, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                orderId, userId, showId, "lock_" + orderId, movieTitle, "测试影院", "一号厅",
                now, "[\"sm_any:1:1\"]", 50.00, 50.00, "[{\"seatName\":\"1排1座\"}]", "issued",
                "TKT-" + orderId, "desktop_button", now, now, now);
    }

    private void insertClick(String movieId, int cnt) {
        jdbcTemplate.update(
                "INSERT INTO reco_clicks(movie_id, click_date, cnt) VALUES (?,?,?)",
                movieId, LocalDate.now(), cnt);
    }

    /** 注册演示用户甲（幂等），并把 user_profile 偏好写为指定类型 */
    private void setUserProfile(String nickname, String genresJson) throws Exception {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM user_profile WHERE user_id = (SELECT user_id FROM user_account WHERE nickname = ?)",
                Integer.class, nickname);
        OffsetDateTime now = OffsetDateTime.now();
        if (exists == null || exists == 0) {
            jdbcTemplate.update(
                    "INSERT INTO user_profile(user_id, prefer_genres_json, prefer_row, prefer_side, updated_at) "
                            + "VALUES ((SELECT user_id FROM user_account WHERE nickname = ?),?,?,?,?)",
                    nickname, genresJson, null, null, now);
        } else {
            jdbcTemplate.update(
                    "UPDATE user_profile SET prefer_genres_json = ?, updated_at = ? "
                            + "WHERE user_id = (SELECT user_id FROM user_account WHERE nickname = ?)",
                    genresJson, now, nickname);
        }
    }

    private String loginAs(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
