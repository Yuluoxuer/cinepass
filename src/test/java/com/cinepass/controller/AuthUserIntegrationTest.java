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
    void seedMovie() {
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

        String staffToken = loginAs("运营小李", "ChangeMe123");
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        String adminToken = loginAs("系统管理员", "Admin12345");
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
                                + "\"password\":\"StaffPass1\",\"role\":\"staff\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("staff"))
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
