package com.cinepass.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cinepass.service.EsSearchService;
import com.cinepass.service.EsIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * 影院、影厅和座位图接口的端到端权限与查询测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CinemaIntegrationTest {

    private static final String STAFF_CINEMA_ID = "c_cinema_test_staff";
    private static final String OTHER_CINEMA_ID = "c_cinema_test_other";

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
    void setUp() {
        insertCinema(STAFF_CINEMA_ID, "员工所属影院", 31.230400, 121.473700, "地铁 2 号线直达", "[\"杜比\"]");
        insertCinema(OTHER_CINEMA_ID, "其他影院", 31.280400, 121.523700, "公交 88 路", "[\"IMAX\"]");
        jdbcTemplate.update("UPDATE user_account SET cinema_id = ? WHERE nickname = ?", STAFF_CINEMA_ID, "运营小王");
    }

    @Test
    void publicCinemaListSortsByDistanceAndDetailIncludesHalls() throws Exception {
        insertHall("h_cinema_test_detail", STAFF_CINEMA_ID, "sm_cinema_test_detail", "一号厅");

        mockMvc.perform(get("/api/v1/cinemas")
                        .param("lat", "31.230400")
                        .param("lng", "121.473700")
                        .param("radiusMeters", "10000")
                        .param("sort", "distance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].cinemaId").value(STAFF_CINEMA_ID))
                .andExpect(jsonPath("$.data.items[0].distanceMeters").value(0))
                .andExpect(jsonPath("$.data.items[0].minPrice").doesNotExist());

        mockMvc.perform(get("/api/v1/cinemas/{cinemaId}", STAFF_CINEMA_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cinemaId").value(STAFF_CINEMA_ID))
                .andExpect(jsonPath("$.data.cityName").value("上海市"))
                .andExpect(jsonPath("$.data.trafficNote").value("地铁 2 号线直达"))
                .andExpect(jsonPath("$.data.tags[0]").value("杜比"))
                .andExpect(jsonPath("$.data.halls[0].hallId").value("h_cinema_test_detail"));
    }

    @Test
    void cinemaKeywordSearchFallsBackToMysqlWithKeywordFilterWhenEsFails() throws Exception {
        when(esSearchService.searchCinemas(eq("员工所属"), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(1), eq(10)))
                .thenThrow(new RuntimeException("ES unavailable"));

        mockMvc.perform(get("/api/v1/cinemas")
                        .param("q", "员工所属")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].cinemaId").value(STAFF_CINEMA_ID));
    }

    @Test
    void defaultCinemaListFallsBackToMysqlWhenEsFails() throws Exception {
        when(esSearchService.searchCinemas(isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(1), eq(10)))
                .thenThrow(new RuntimeException("ES unavailable"));

        mockMvc.perform(get("/api/v1/cinemas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void publicCinemaListCanSortByPriceWithoutLocation() throws Exception {
        mockMvc.perform(get("/api/v1/cinemas")
                        .param("sort", "price")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void adminCanCreateCinemaAndStaffCannot() throws Exception {
        String body = "{\"cityId\":\"city_test\",\"cityName\":\"上海市\",\"name\":\"新建影院\",\"address\":\"测试路 1 号\","
                + "\"lat\":31.240000,\"lng\":121.480000,\"trafficNote\":\"步行可达\",\"tags\":[\"激光\"]}";

        MvcResult created = mockMvc.perform(post("/api/v1/admin/cinemas")
                        .header("Authorization", bearer(loginAs("系统管理员", "demo123456")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cinemaId").value(org.hamcrest.Matchers.startsWith("c")))
                .andExpect(jsonPath("$.data.cityName").value("上海市"))
                .andExpect(jsonPath("$.data.name").value("新建影院"))
                .andReturn();
        String createdCinemaId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("cinemaId").asText();
        verify(esIndexService).syncCinema(createdCinemaId);

        mockMvc.perform(post("/api/v1/admin/cinemas")
                        .header("Authorization", bearer(loginAs("运营小王", "demo123456")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void creatingCinemaRequiresCityName() throws Exception {
        String body = "{\"name\":\"缺少城市名称的影院\",\"address\":\"测试路 2 号\","
                + "\"lat\":31.240000,\"lng\":121.480000}";

        mockMvc.perform(post("/api/v1/admin/cinemas")
                        .header("Authorization", bearer(loginAs("系统管理员", "demo123456")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void adminCanSoftDeleteCinemaAndStaffCannot() throws Exception {
        String adminToken = bearer(loginAs("系统管理员", "demo123456"));
        String staffToken = bearer(loginAs("运营小王", "demo123456"));
        insertHall("h_cinema_test_other", OTHER_CINEMA_ID, "sm_cinema_test_other", "待删除影院影厅");

        mockMvc.perform(delete("/api/v1/admin/cinemas/{cinemaId}", OTHER_CINEMA_ID)
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        jdbcTemplate.update("UPDATE user_account SET cinema_id = ? WHERE nickname = ?", OTHER_CINEMA_ID, "运营小王");
        mockMvc.perform(delete("/api/v1/admin/cinemas/{cinemaId}", OTHER_CINEMA_ID)
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        jdbcTemplate.update("UPDATE user_account SET cinema_id = ? WHERE cinema_id = ?",
                STAFF_CINEMA_ID, OTHER_CINEMA_ID);
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM user_account WHERE cinema_id = ?", Integer.class, OTHER_CINEMA_ID).intValue());

        mockMvc.perform(delete("/api/v1/admin/cinemas/{cinemaId}", OTHER_CINEMA_ID)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(esIndexService).deleteCinema(OTHER_CINEMA_ID);

        mockMvc.perform(get("/api/v1/cinemas/{cinemaId}", OTHER_CINEMA_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT is_delete FROM cinema WHERE cinema_id = ?", Boolean.class, OTHER_CINEMA_ID)));

        mockMvc.perform(get("/api/v1/cinemas")
                        .param("sort", "price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(put("/api/v1/admin/halls/{hallId}", "h_cinema_test_other")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"已删除影院影厅\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void staffCanListGetUpdateAndDeleteOwnSeatMap() throws Exception {
        String staffToken = bearer(loginAs("运营小王", "demo123456"));
        String seatMapId = "sm_cinema_test_crud";

        mockMvc.perform(post("/api/v1/seat-maps").header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(seatMapBodyWithoutCinema(seatMapId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatMapId").value(seatMapId))
                .andExpect(jsonPath("$.data.cinemaId").value(STAFF_CINEMA_ID));

        mockMvc.perform(get("/api/v1/seat-maps").header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.seatMapId=='" + seatMapId + "')].seatMapId").exists());

        mockMvc.perform(get("/api/v1/seat-maps/{seatMapId}", seatMapId).header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatCount").value(2))
                .andExpect(jsonPath("$.data.mutable").value(true));

        String updateBody = "{\"rows\":2,\"cols\":3,\"screenLabel\":\"银幕更新\",\"seats\":["
                + "{\"graphRow\":1,\"graphCol\":1},"
                + "{\"graphRow\":1,\"graphCol\":3},"
                + "{\"graphRow\":2,\"graphCol\":2}]}";
        mockMvc.perform(put("/api/v1/seat-maps/{seatMapId}", seatMapId).header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.screenLabel").value("银幕更新"))
                .andExpect(jsonPath("$.data.seatCount").value(3));

        mockMvc.perform(delete("/api/v1/seat-maps/{seatMapId}", seatMapId).header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.seatMapId").value(seatMapId));

        mockMvc.perform(get("/api/v1/seat-maps/{seatMapId}", seatMapId).header("Authorization", staffToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffCanOnlyManageSeatMapsAndHallsForAssignedCinema() throws Exception {
        String staffToken = bearer(loginAs("运营小王", "demo123456"));
        String ownSeatMap = "sm_cinema_test_own";
        String ownSeatMapBody = seatMapBodyWithoutCinema(ownSeatMap);

        mockMvc.perform(post("/api/v1/seat-maps").header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(ownSeatMapBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatMapId").value(ownSeatMap))
                .andExpect(jsonPath("$.data.cinemaId").value(STAFF_CINEMA_ID))
                .andExpect(jsonPath("$.data.seatCount").value(2))
                .andExpect(jsonPath("$.data.seats[0].graphRow").value(1))
                .andExpect(jsonPath("$.data.seats[0].graphCol").value(2))
                .andExpect(jsonPath("$.data.seats[0].rowNo").value(1))
                .andExpect(jsonPath("$.data.seats[0].colNo").value(1))
                .andExpect(jsonPath("$.data.seats[0].type").value("normal"))
                .andExpect(jsonPath("$.data.seats[0].defaultStatus").value("available"));

        mockMvc.perform(post("/api/v1/halls").header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hallId\":\"h_cinema_test_own\",\"name\":\"员工影厅\",\"seatMapId\":\""
                                + ownSeatMap + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hallId").value("h_cinema_test_own"))
                .andExpect(jsonPath("$.data.cinemaId").value(STAFF_CINEMA_ID));

        mockMvc.perform(post("/api/v1/seat-maps").header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(seatMapBody("sm_cinema_test_denied", OTHER_CINEMA_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/v1/admin/halls").header("Authorization", staffToken)
                        .param("cinemaId", OTHER_CINEMA_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        insertHall("h_cinema_test_other", OTHER_CINEMA_ID, "sm_cinema_test_other", "其他影厅");
        mockMvc.perform(put("/api/v1/admin/halls/{hallId}", "h_cinema_test_other")
                        .header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"越权修改\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private void insertCinema(String cinemaId, String name, double lat, double lng, String trafficNote, String tagsJson) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM cinema WHERE cinema_id = ?", Integer.class, cinemaId);
        if (count != null && count == 0) {
            OffsetDateTime now = OffsetDateTime.now();
            jdbcTemplate.update("INSERT INTO cinema(cinema_id, city_id, city_name, name, address, lat, lng, traffic_note, tags_json, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    cinemaId, "city_test", "上海市", name, "测试地址", lat, lng, trafficNote, tagsJson, now, now);
        }
    }

    private void insertSeatMap(String seatMapId, String cinemaId) {
        jdbcTemplate.update("INSERT INTO seat_map(seat_map_id, cinema_id, rows_n, cols_n, screen_label, mutable) VALUES (?,?,?,?,?,?)",
                seatMapId, cinemaId, 1, 2, "银幕", true);
    }

    private void insertHall(String hallId, String cinemaId, String seatMapId, String name) {
        insertSeatMap(seatMapId, cinemaId);
        jdbcTemplate.update("INSERT INTO hall(hall_id, cinema_id, name, seat_map_id) VALUES (?,?,?,?)",
                hallId, cinemaId, name, seatMapId);
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

    private String seatMapBody(String seatMapId, String cinemaId) {
        return "{\"seatMapId\":\"" + seatMapId + "\",\"cinemaId\":\"" + cinemaId
                + "\",\"rows\":1,\"cols\":2,\"screenLabel\":\"银幕\",\"seats\":["
                + "{\"graphRow\":1,\"graphCol\":1,\"rowNo\":1,\"colNo\":1,\"seatName\":\"1排1座\",\"type\":\"normal\",\"zone\":\"standard\"},"
                + "{\"graphRow\":1,\"graphCol\":2,\"rowNo\":1,\"colNo\":2,\"seatName\":\"1排2座\",\"type\":\"normal\",\"zone\":\"standard\"}]}";
    }

    private String seatMapBodyWithoutCinema(String seatMapId) {
        return "{\"seatMapId\":\"" + seatMapId
                + "\",\"rows\":2,\"cols\":3,\"screenLabel\":\"银幕\",\"seats\":["
                + "{\"graphRow\":1,\"graphCol\":2},"
                + "{\"graphRow\":2,\"graphCol\":1}]}";
    }
}
