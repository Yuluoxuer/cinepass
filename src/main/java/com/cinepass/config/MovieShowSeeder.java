package com.cinepass.config;

import com.alibaba.fastjson2.JSON;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.HallMapper;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Hall;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
import com.cinepass.util.MovieIds;
import com.cinepass.util.ShowIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 演示用种子数据 — 仅在数据库为空时写入电影和场次。
 * 不依赖其他模块的 Controller/Service。
 */
@Slf4j
@Component
public class MovieShowSeeder implements CommandLineRunner {

    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final ShowMapper showMapper;

    public MovieShowSeeder(MovieMapper movieMapper, CinemaMapper cinemaMapper,
                           HallMapper hallMapper, ShowMapper showMapper) {
        this.movieMapper = movieMapper;
        this.cinemaMapper = cinemaMapper;
        this.hallMapper = hallMapper;
        this.showMapper = showMapper;
    }

    @Override
    public void run(String... args) {
        long movieCount = movieMapper.countFiltered(null, null, null);
        if (movieCount > 0) {
            log.info("[MovieShowSeeder] 已有 {} 部影片，跳过种子", movieCount);
            return;
        }
        try {
            seedAll();
        } catch (Exception e) {
            log.warn("[MovieShowSeeder] 种子数据写入失败，跳过: {}", e.getMessage());
        }
    }

    private void seedAll() {
        seedCinemaHall();
        seedMovies();
        seedShows();
        log.info("[MovieShowSeeder] 种子数据写入完成");
    }

    private void seedCinemaHall() {
        OffsetDateTime now = OffsetDateTime.now();
        // 影院 1
        Cinema c1 = new Cinema();
        c1.setCinemaId("c0000000000000000000000000000001");
        c1.setCityId("city_sh");
        c1.setName("万达影城（五角场店）");
        c1.setAddress("淞沪路 77 号");
        c1.setLat(bd("31.2989"));
        c1.setLng(bd("121.5140"));
        c1.setCreatedAt(now);
        c1.setUpdatedAt(now);
        if (cinemaMapper.selectById(c1.getCinemaId()) == null) cinemaMapper.insert(c1);

        // 影厅 1-1
        Hall h1 = new Hall();
        h1.setHallId("h0000000000000000000000000000001");
        h1.setCinemaId(c1.getCinemaId());
        h1.setName("1号厅");
        h1.setSeatMapId("sm_rect_1");
        if (hallMapper.selectById(h1.getHallId()) == null) hallMapper.insert(h1);

        // 影院 2
        Cinema c2 = new Cinema();
        c2.setCinemaId("c0000000000000000000000000000002");
        c2.setCityId("city_sh");
        c2.setName("金逸影城（大学路店）");
        c2.setAddress("大学路 297 号");
        c2.setLat(bd("31.3005"));
        c2.setLng(bd("121.5050"));
        c2.setCreatedAt(now);
        c2.setUpdatedAt(now);
        if (cinemaMapper.selectById(c2.getCinemaId()) == null) cinemaMapper.insert(c2);

        // 影厅 2-1
        Hall h2 = new Hall();
        h2.setHallId("h0000000000000000000000000000002");
        h2.setCinemaId(c2.getCinemaId());
        h2.setName("IMAX厅");
        h2.setSeatMapId("sm_rect_1");
        if (hallMapper.selectById(h2.getHallId()) == null) hallMapper.insert(h2);

        log.info("[MovieShowSeeder] 影院/影厅 OK");
    }

    private void seedMovies() {
        OffsetDateTime now = OffsetDateTime.now();
        insertMovie("流浪地球 3", "https://picsum.photos/seed/wandering3/300/450",
                genres("科幻", "冒险"), bd("9.1"), 173, "2026-02-01", "hot_showing",
                "太阳急速老化，人类面临前所未有的生存危机。联合政府决定启动移山计划……",
                "吴京 / 刘德华 / 李雪健", 12890, now);
        insertMovie("年会不能停！", "https://picsum.photos/seed/nianhui/300/450",
                genres("喜剧"), bd("8.2"), 118, "2026-01-10", "hot_showing",
                "打工人胡建林被错调总部，展开了一场啼笑皆非的职场逆袭……",
                "大鹏 / 白客", 5600, now);
        insertMovie("热辣滚烫", "https://picsum.photos/seed/rela/300/450",
                genres("喜剧", "运动"), bd("8.0"), 133, "2026-02-10", "hot_showing",
                "失业在家的乐莹，偶然接触到拳击，人生从此改变……",
                "贾玲 / 雷佳音", 4200, now);
        insertMovie("封神第二部", "https://picsum.photos/seed/fengshen2/300/450",
                genres("动作", "奇幻"), bd("8.5"), 148, "2026-03-15", "hot_showing",
                "姬发回到西岐，商王殷寿率大军压境……",
                "费翔 / 黄渤 / 于适", 9800, now);
        insertMovie("熊出没·逆转时空", "https://picsum.photos/seed/xiongchumo/300/450",
                genres("动画", "喜剧"), bd("7.8"), 99, "2026-01-25", "hot_showing",
                "光头强意外穿越时空，展开了一场惊险刺激的冒险……",
                "谭笑 / 张伟 / 张秉君", 3200, now);
        insertMovie("星际穿越2", "https://picsum.photos/seed/star2/300/450",
                genres("科幻", "冒险"), null, 169, "2026-09-01", "coming_soon",
                "人类发现新星系后，新一轮星际移民计划启动……",
                "提莫西·查拉梅 / 赞达亚", 0, now);
        log.info("[MovieShowSeeder] 影片 OK");
    }

    private void seedShows() {
        OffsetDateTime now = OffsetDateTime.now();
        // 万达 1号厅：流浪地球 3 的 4 场
        insertShow("c0000000000000000000000000000001", "h0000000000000000000000000000001",
                "2026-08-03T14:10:00+08:00", "2026-08-03T17:05:00+08:00", bd("55"));
        insertShow("c0000000000000000000000000000001", "h0000000000000000000000000000001",
                "2026-08-03T16:40:00+08:00", "2026-08-03T19:35:00+08:00", bd("55"));
        insertShow("c0000000000000000000000000000001", "h0000000000000000000000000000001",
                "2026-08-03T19:20:00+08:00", "2026-08-03T22:15:00+08:00", bd("65"));
        // 金逸 IMAX厅：流浪地球 3 的 3 场
        insertShow("c0000000000000000000000000000002", "h0000000000000000000000000000002",
                "2026-08-03T13:00:00+08:00", "2026-08-03T15:55:00+08:00", bd("88"));
        insertShow("c0000000000000000000000000000002", "h0000000000000000000000000000002",
                "2026-08-03T18:00:00+08:00", "2026-08-03T20:55:00+08:00", bd("98"));
        insertShow("c0000000000000000000000000000002", "h0000000000000000000000000000002",
                "2026-08-03T21:30:00+08:00", "2026-08-04T00:25:00+08:00", bd("88"));
        log.info("[MovieShowSeeder] 场次 OK");
    }

    private void insertMovie(String title, String posterUrl, String genresJson,
                             BigDecimal rating, int durationMin, String releaseDate,
                             String status, String description, String cast, int wantSee, OffsetDateTime now) {
        Movie m = movieMapper.selectById(movieIdFromTitle(title));
        if (m != null) return;
        m = new Movie();
        m.setMovieId(movieIdFromTitle(title));
        m.setTitle(title);
        m.setPosterUrl(posterUrl);
        m.setGenresJson(genresJson);
        m.setRating(rating);
        m.setDurationMin(durationMin);
        m.setReleaseDate(LocalDate.parse(releaseDate));
        m.setStatus(status);
        m.setDescription(description);
        m.setCastText(cast);
        m.setWantSeeCount(wantSee);
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        movieMapper.insert(m);
    }

    /** 场次 movieId 取第一部影片（流浪地球 3） */
    private void insertShow(String cinemaId, String hallId,
                            String startTime, String endTime, BigDecimal price) {
        String movieId = movieIdFromTitle("流浪地球 3");
        OffsetDateTime start = OffsetDateTime.parse(startTime);
        OffsetDateTime end = OffsetDateTime.parse(endTime);
        List<ShowSchedule> overlaps = showMapper.findOverlapping(hallId, start, end, null);
        if (overlaps != null && !overlaps.isEmpty()) return;
        ShowSchedule s = new ShowSchedule();
        s.setShowId(ShowIds.next());
        s.setMovieId(movieId);
        s.setCinemaId(cinemaId);
        s.setHallId(hallId);
        s.setSeatMapId("sm_rect_1");
        s.setStartTime(start);
        s.setEndTime(end);
        s.setPrice(price);
        s.setStatus("on_sale");
        s.setCreatedAt(OffsetDateTime.now());
        s.setUpdatedAt(OffsetDateTime.now());
        showMapper.insert(s);
    }

    private static String movieIdFromTitle(String title) {
        int h = title.hashCode() & 0x7FFFFFFF;
        return "m" + String.format("%031d", h);
    }

    private static String genres(String... values) {
        return JSON.toJSONString(values);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value).setScale(6, RoundingMode.HALF_UP);
    }
}
