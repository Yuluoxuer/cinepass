package com.cinepass.config;

import com.cinepass.service.EsIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/**
 * 到上映日自动上架定时任务集成测试：release_date <= today 的 coming_soon → hot_showing。
 * <p>直接通过 jdbcTemplate 构造 movie 行，验证候选查询与条件更新 SQL；ES 用 mock，验证翻转后逐个同步。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MovieStatusSchedulerTest {

    @Autowired
    private MovieStatusScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private EsIndexService esIndexService;

    @Test
    void runOnce_releaseDateDue_shouldFlipToHotShowingAndSyncEs() {
        LocalDate today = LocalDate.now();
        insertMovie("m_due_yesterday", today.minusDays(1), "coming_soon");
        insertMovie("m_due_today", today, "coming_soon");

        int n = scheduler.runOnce(today);

        assertThat(n).isEqualTo(2);
        assertStatus("m_due_yesterday", "hot_showing");
        assertStatus("m_due_today", "hot_showing");
        verify(esIndexService, times(2)).syncMovie(anyString());
    }

    @Test
    void runOnce_futureRelease_shouldStayComingSoon() {
        LocalDate today = LocalDate.now();
        insertMovie("m_future", today.plusDays(1), "coming_soon");

        int n = scheduler.runOnce(today);

        assertThat(n).isZero();
        assertStatus("m_future", "coming_soon");
        verify(esIndexService, never()).syncMovie(anyString());
    }

    @Test
    void runOnce_alreadyHotShowingOrOff_shouldNotBeTouched() {
        LocalDate today = LocalDate.now();
        insertMovie("m_hot", today.minusDays(10), "hot_showing");
        insertMovie("m_off", today.minusDays(1), "off");

        int n = scheduler.runOnce(today);

        assertThat(n).isZero();
        assertStatus("m_hot", "hot_showing");
        assertStatus("m_off", "off");
    }

    @Test
    void runOnce_esSyncFailure_shouldStillFlipAndNotThrow() {
        LocalDate today = LocalDate.now();
        insertMovie("m_fail", today, "coming_soon");
        doThrow(new RuntimeException("es down")).when(esIndexService).syncMovie("m_fail");

        int n = scheduler.runOnce(today);

        assertThat(n).isEqualTo(1);
        assertStatus("m_fail", "hot_showing");
    }

    private void insertMovie(String movieId, LocalDate releaseDate, String status) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO movie(movie_id, title, poster_url, genres_json, rating, duration_min, "
                        + "release_date, status, description, cast_text, want_see_count, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                movieId, "测试片" + movieId, "http://x/" + movieId + ".jpg", "[]",
                new BigDecimal("8.0"), 120, releaseDate, status,
                "desc", null, 0, now, now);
    }

    private void assertStatus(String movieId, String expected) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM movie WHERE movie_id = ?", String.class, movieId);
        assertThat(status).isEqualTo(expected);
    }
}
