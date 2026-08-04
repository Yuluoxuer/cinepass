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

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户认证 / 个人中心 / 想看 / 管理员账号 — MockMvc 全栈集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthUserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedMovie() throws Exception {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM movie WHERE movie_id = ?", Integer.class, "m_test_001");
        if (count != null && count == 0) {
            OffsetDateTime now = OffsetDateTime.now();
            jdbcTemplate.update(
                    "INSERT INTO movie(movie_id, title, poster_url, genres_json, rating, duration_min, "
                            + "release_date, status, description, cast_text, want_see_count, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    "m_test_001", "测试影片", "http://example.com/p.jpg", "[\"剧情\"]", 8.5,
                    120, LocalDate.of(2026, 1, 1), "showing", "desc", "cast", 0, now, now);
        }
        // 经真实注册接口建号（u+uuid7），再按需提升角色；不写死假 user_id
        ensureAccountViaRegister("演示用户甲", "13800000001", "demo123456", "user");
        ensureAccountViaRegister("运营小王", "13900000002", "demo123456", "staff");
        ensureAccountViaRegister("系统管理员", "13900000001", "demo123456", "admin");
    }

    /** 调用 /auth/register 创建账号；非 user 角色用 JDBC 提升（避免再依赖种子 SQL）。 */
    private void ensureAccountViaRegister(String nickname, String phone, String password, String role)
            throws Exception {
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
        if (!"user".equals(role)) {
            jdbcTemplate.update("UPDATE user_account SET role = ?, cinema_id = NULL WHERE nickname = ?",
                    role, nickname);
        }
    }

    @Test
    void registerLoginMeChangePasswordLogout() throws Exception {
        String nickname = "it_user_" + System.nanoTime();
        String phone = "138" + String.format("%08d", System.nanoTime() % 100000000L);

        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("user"))
                .andReturn();

        String token = readData(register).path("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andExpect(jsonPath("$.data.role").value("user"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + nickname + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"password1\",\"newPassword\":\"password2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + nickname + "\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        MvcResult relogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + nickname + "\",\"password\":\"password2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String token2 = readData(relogin).path("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loggedOut").value(true));
    }

    @Test
    void profileAndWantSee() throws Exception {
        String token = loginAs("演示用户甲", "demo123456");

        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferGenres\":[\"喜剧\"],\"preferRow\":\"middle\",\"preferSide\":\"center\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferRow").value("middle"))
                .andExpect(jsonPath("$.data.preferSide").value("center"));

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferRow").value("middle"));

        mockMvc.perform(post("/api/v1/movies/m_test_001/want-see")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wanted").value(true));

        mockMvc.perform(get("/api/v1/me/want-see").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].movieId").value("m_test_001"));

        mockMvc.perform(delete("/api/v1/movies/m_test_001/want-see")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wanted").value(false));
    }

    @Test
    void adminUsersRequireAdminRole() throws Exception {
        String userToken = loginAs("演示用户甲", "demo123456");
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        String staffToken = loginAs("运营小王", "demo123456");
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        String adminToken = loginAs("系统管理员", "demo123456");
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());

        String nick = "staff_" + System.nanoTime();
        String staffPhone = "137" + String.format("%08d", System.nanoTime() % 100000000L);
        MvcResult created = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nick + "\",\"phone\":\"" + staffPhone + "\","
                                + "\"password\":\"StaffPass1\",\"role\":\"staff\",\"cinemaId\":\"c12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("staff"))
                .andExpect(jsonPath("$.data.cinemaId").value("c12"))
                .andReturn();
        String userId = readData(created).path("userId").asText();
        assertThat(userId).startsWith("u");

        mockMvc.perform(put("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));
    }

    @Test
    void unauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_invalidPhoneAndShortPassword_shouldParamError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"bad_phone\",\"phone\":\"12345\",\"password\":\"password1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"short_pwd\",\"phone\":\"13900001111\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void register_duplicateNicknameOrPhone_shouldConflictOrBizError() throws Exception {
        String nick = "dup_" + System.nanoTime();
        String phone = "135" + String.format("%08d", System.nanoTime() % 100000000L);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nick + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nick + "\",\"phone\":\"135"
                                + String.format("%08d", (System.nanoTime() + 1) % 100000000L)
                                + "\",\"password\":\"password1\"}"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"other_" + System.nanoTime() + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"password1\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void login_wrongPassword_shouldUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"演示用户甲\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void login_unknownAccount_shouldClientError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"no_such_user_xyz\",\"password\":\"password1\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void wantSee_unknownMovie_shouldNotFoundOrBiz() throws Exception {
        String token = loginAs("演示用户甲", "demo123456");
        mockMvc.perform(post("/api/v1/movies/m_not_exist/want-see")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void meEndpoints_withoutToken_shouldUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/want-see")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCreate_staffWithoutCinema_shouldParamError() throws Exception {
        String adminToken = loginAs("系统管理员", "demo123456");
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"no_cinema_" + System.nanoTime()
                                + "\",\"password\":\"StaffPass1\",\"role\":\"staff\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void adminCreate_userWithCinema_shouldParamError() throws Exception {
        String adminToken = loginAs("系统管理员", "demo123456");
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"user_c_" + System.nanoTime()
                                + "\",\"password\":\"UserPass12\",\"role\":\"user\",\"cinemaId\":\"c12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void adminCreate_invalidRole_shouldParamError() throws Exception {
        String adminToken = loginAs("系统管理员", "demo123456");
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"bad_role_" + System.nanoTime()
                                + "\",\"password\":\"UserPass12\",\"role\":\"superadmin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void adminCreate_shortPassword_shouldParamError() throws Exception {
        String adminToken = loginAs("系统管理员", "demo123456");
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"short_pwd_" + System.nanoTime()
                                + "\",\"password\":\"short\",\"role\":\"user\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void adminUpdate_selfDisable_shouldFail() throws Exception {
        String adminToken = loginAs("系统管理员", "demo123456");
        MvcResult me = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String adminId = readData(me).path("userId").asText();

        mockMvc.perform(put("/api/v1/admin/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void adminUpdate_missingUser_shouldNotFound() throws Exception {
        String adminToken = loginAs("系统管理员", "demo123456");
        mockMvc.perform(put("/api/v1/admin/users/u00000000000000000000000000000000")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void adminPage_boundariesAndFilter_shouldWork() throws Exception {
        String adminToken = loginAs("系统管理员", "demo123456");
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "0")
                        .param("size", "999")
                        .param("role", "admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void adminCreateUpdate_fullLifecycle_staffCinemaAndDisable() throws Exception {
        String adminToken = loginAs("系统管理员", "demo123456");
        String nick = "lifecycle_" + System.nanoTime();
        String phone = "136" + String.format("%08d", System.nanoTime() % 100000000L);

        MvcResult created = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nick + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"StaffPass1\",\"role\":\"staff\",\"cinemaId\":\"c12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("staff"))
                .andExpect(jsonPath("$.data.cinemaId").value("c12"))
                .andExpect(jsonPath("$.data.phone").value(org.hamcrest.Matchers.containsString("****")))
                .andReturn();
        String userId = readData(created).path("userId").asText();

        // staff 可登录
        String staffToken = loginAs(nick, "StaffPass1");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("staff"))
                .andExpect(jsonPath("$.data.cinemaId").value("c12"));

        // 改角色为 user 并清空影院、禁用
        mockMvc.perform(put("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"user\",\"cinemaId\":\"\",\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("user"))
                .andExpect(jsonPath("$.data.status").value(0));

        // 禁用后无法登录
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + nick + "\",\"password\":\"StaffPass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void adminUsers_postWithoutToken_shouldUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"x\",\"password\":\"UserPass12\",\"role\":\"user\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_disabledSeedUser_shouldUnauthorized() throws Exception {
        jdbcTemplate.update("UPDATE user_account SET status = 0 WHERE nickname = ?", "演示用户甲");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"演示用户甲\",\"password\":\"demo123456\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    private String loginAs(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        return readData(result).path("accessToken").asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data");
    }
}
