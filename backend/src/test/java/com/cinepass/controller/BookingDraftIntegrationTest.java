package com.cinepass.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BookingDraft 公开接口集成测试：创建 / hydrate / CAS / 依赖清空。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingDraftIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createGetUpdate_andConflict() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/booking-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"manual\",\"movieId\":\"m_draft_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(org.hamcrest.Matchers.startsWith("sess")))
                .andExpect(jsonPath("$.data.movieId").value("m_draft_1"))
                .andExpect(jsonPath("$.data.state").value("SelectCinema"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        String sid = data.path("sessionId").asText();

        mockMvc.perform(get("/api/v1/booking-drafts/" + sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sid))
                .andExpect(jsonPath("$.data.version").value(0));

        MvcResult updated = mockMvc.perform(put("/api/v1/booking-drafts/" + sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"patch\":{\"cinemaId\":\"c_1\",\"count\":2}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.cinemaId").value("c_1"))
                .andExpect(jsonPath("$.data.state").value("SelectShow"))
                .andReturn();

        // 改影院清空 show 以下
        mockMvc.perform(put("/api/v1/booking-drafts/" + sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"patch\":{\"showId\":\"s_1\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showId").value("s_1"))
                .andExpect(jsonPath("$.data.state").value("SelectSeat"));

        mockMvc.perform(put("/api/v1/booking-drafts/" + sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"patch\":{\"cinemaId\":\"c_2\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cinemaId").value("c_2"))
                .andExpect(jsonPath("$.data.showId").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(3));

        // CAS 冲突
        mockMvc.perform(put("/api/v1/booking-drafts/" + sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"patch\":{\"count\":3}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.data.errorCode").value("DRAFT_CONFLICT"))
                .andExpect(jsonPath("$.data.serverDraft.version").value(3));

        // 懒创建
        mockMvc.perform(get("/api/v1/booking-drafts/sess_lazy_new_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("sess_lazy_new_1"))
                .andExpect(jsonPath("$.data.state").value("Idle"));

        assertThat(updated.getResponse().getContentAsString()).contains("c_1");
    }
}
