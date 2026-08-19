package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.SeatStatusMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.mapper.ShowZonePriceMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
import com.cinepass.model.ShowZonePrice;
import com.cinepass.service.ShowService;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.ShowDetailVO;
import com.cinepass.vo.ShowListResult;
import com.cinepass.vo.ShowVO;
import com.cinepass.util.DateTimeFormats;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link ShowService} 实现。
 */
@Service
public class ShowServiceImpl implements ShowService {

    private final ShowMapper showMapper;
    private final SeatStatusMapper seatStatusMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final ShowZonePriceMapper showZonePriceMapper;

    public ShowServiceImpl(ShowMapper showMapper,
                           SeatStatusMapper seatStatusMapper,
                           MovieMapper movieMapper,
                           CinemaMapper cinemaMapper,
                           ShowZonePriceMapper showZonePriceMapper) {
        this.showMapper = showMapper;
        this.seatStatusMapper = seatStatusMapper;
        this.movieMapper = movieMapper;
        this.cinemaMapper = cinemaMapper;
        this.showZonePriceMapper = showZonePriceMapper;
    }

    // 按影院+影片+日期查询场次列表，将实体转为VO后封装日期+列表结果返回
    @Override
    public ShowListResult list(String cinemaId, String movieId, String date) {
        List<ShowSchedule> rows = showMapper.listByMovieCinemaDate(cinemaId, movieId, date);
        return ShowListResult.builder().date(date).items(buildShowVosWithZonePrices(rows)).build();
    }

    // 查询某影院某影片的不区分日期的全部场次
    @Override
    public ShowListResult listAll(String cinemaId, String movieId, OffsetDateTime after) {
        List<ShowSchedule> rows = showMapper.listByMovieCinema(cinemaId, movieId, after);
        return ShowListResult.builder().date(null).items(buildShowVosWithZonePrices(rows)).build();
    }

    // 查某影院当前在售影片列表：校验影院存在→查询每个影片的最早未来场次→组装VO并附带最近排片日
    @Override
    public PageResult<MovieVO> listOnSaleMovies(String cinemaId) {
        if (cinemaMapper.selectById(cinemaId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影院不存在");
        }
        OffsetDateTime after = DateTimeFormats.now();
        List<ShowSchedule> earliest = showMapper.listEarliestUpcomingByCinema(cinemaId, after);
        if (earliest == null || earliest.isEmpty()) {
            return new PageResult<>(Collections.<MovieVO>emptyList(), 1, 0, 0);
        }
        List<MovieVO> items = new ArrayList<>();
        for (ShowSchedule row : earliest) {
            if (row == null || row.getMovieId() == null) {
                continue;
            }
            MovieVO vo = buildMovieVO(row.getMovieId());
            if (vo == null) {
                continue;
            }
            if (row.getStartTime() != null) {
                vo.setNextShowDate(row.getStartTime().atZoneSameInstant(DateTimeFormats.ZONE).toLocalDate().toString());
            }
            items.add(vo);
        }
        return new PageResult<>(items, 1, items.size(), items.size());
    }

    // 查场次详情，组装场次VO + 影片VO + 影院简要信息
    @Override
    public ShowDetailVO get(String showId) {
        ShowSchedule s = showMapper.selectById(showId);
        if (s == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        ShowVO showVO = buildShowVO(s);
        MovieVO movieVO = buildMovieVO(s.getMovieId());
        ShowDetailVO.CinemaBrief cinemaBrief = buildCinemaBrief(s.getCinemaId());
        return ShowDetailVO.builder()
                .showId(showVO.getShowId()).movieId(showVO.getMovieId())
                .cinemaId(showVO.getCinemaId()).hallId(showVO.getHallId())
                .hallName(showVO.getHallName())
                .startTime(showVO.getStartTime()).endTime(showVO.getEndTime())
                .price(showVO.getPrice())
                .seatRemain(showVO.getSeatRemain()).seatRemainLevel(showVO.getSeatRemainLevel())
                .movie(movieVO).cinema(cinemaBrief).build();
    }

    @Override
    public ShowVO buildShowVO(ShowSchedule s) {
        return buildShowVO(s, loadZonePriceVos(s.getShowId()));
    }

    // 场次实体→VO：统计余座、计算余座等级、格式化时间、组装分区价格
    @Override
    public ShowVO buildShowVO(ShowSchedule s, List<ShowVO.ZonePriceVO> zonePrices) {
        int total = seatStatusMapper.countTotal(s.getShowId());
        int available = seatStatusMapper.countAvailable(s.getShowId());
        String level = calcSeatRemainLevel(total, available);
        List<ShowVO.ZonePriceVO> resolvedZonePrices = zonePrices != null
                ? zonePrices : loadZonePriceVos(s.getShowId());
        return ShowVO.builder()
                .showId(s.getShowId()).movieId(s.getMovieId()).movieTitle(s.getMovieTitle())
                .cinemaId(s.getCinemaId()).hallId(s.getHallId())
                .hallName(s.getHallName())
                .startTime(s.getStartTime() != null ? DateTimeFormats.format(s.getStartTime()) : null)
                .endTime(s.getEndTime() != null ? DateTimeFormats.format(s.getEndTime()) : null)
                .price(s.getPrice())
                .zonePrices(resolvedZonePrices)
                .seatRemain(available).seatRemainLevel(level)
                .status(s.getStatus())
                .runtimeState(calcRuntimeState(s))
                .build();
    }

    /** 列表批量装配分区价，避免 N+1 */
    private List<ShowVO> buildShowVosWithZonePrices(List<ShowSchedule> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> showIds = new ArrayList<>();
        for (ShowSchedule s : rows) {
            if (s != null && s.getShowId() != null) {
                showIds.add(s.getShowId());
            }
        }
        Map<String, List<ShowVO.ZonePriceVO>> zoneByShow = loadZonePriceVosByShowIds(showIds);
        List<ShowVO> items = new ArrayList<>();
        for (ShowSchedule s : rows) {
            if (s == null) {
                continue;
            }
            items.add(buildShowVO(s, zoneByShow.getOrDefault(s.getShowId(), Collections.emptyList())));
        }
        return items;
    }

    private List<ShowVO.ZonePriceVO> loadZonePriceVos(String showId) {
        if (showId == null) {
            return Collections.emptyList();
        }
        return toZonePriceVos(showZonePriceMapper.selectByShowId(showId));
    }

    private Map<String, List<ShowVO.ZonePriceVO>> loadZonePriceVosByShowIds(List<String> showIds) {
        Map<String, List<ShowVO.ZonePriceVO>> map = new HashMap<>();
        if (showIds == null || showIds.isEmpty()) {
            return map;
        }
        List<ShowZonePrice> rows = showZonePriceMapper.selectByShowIds(showIds);
        if (rows == null) {
            return map;
        }
        for (ShowZonePrice row : rows) {
            if (row == null || row.getShowId() == null) {
                continue;
            }
            map.computeIfAbsent(row.getShowId(), k -> new ArrayList<>())
                    .add(ShowVO.ZonePriceVO.builder()
                            .zone(row.getZone())
                            .price(row.getPrice())
                            .build());
        }
        return map;
    }

    private List<ShowVO.ZonePriceVO> toZonePriceVos(List<ShowZonePrice> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ShowVO.ZonePriceVO> vos = new ArrayList<>();
        for (ShowZonePrice row : rows) {
            if (row == null || row.getZone() == null || row.getPrice() == null) {
                continue;
            }
            vos.add(ShowVO.ZonePriceVO.builder()
                    .zone(row.getZone())
                    .price(row.getPrice())
                    .build());
        }
        return vos;
    }

    // 根据影片ID构建MovieVO：解析genres JSON、评分四舍五入保留1位小数
    private MovieVO buildMovieVO(String movieId) {
        Movie m = movieMapper.selectById(movieId);
        if (m == null) return null;
        List<String> genres = m.getGenresJson() == null ? Collections.<String>emptyList()
                : JSON.parseArray(m.getGenresJson(), String.class);
        BigDecimal rating = m.getRating();
        if (rating != null) rating = rating.setScale(1, RoundingMode.HALF_UP);
        return MovieVO.builder()
                .movieId(m.getMovieId()).title(m.getTitle()).posterUrl(m.getPosterUrl())
                .genres(genres).rating(rating).durationMin(m.getDurationMin())
                .releaseDate(m.getReleaseDate() != null ? m.getReleaseDate().toString() : null)
                .status(m.getStatus()).description(m.getDescription())
                .cast(m.getCastText()).wantSeeCount(m.getWantSeeCount()).build();
    }

    // 根据影院ID构建影院简要信息（名称+地址）
    private ShowDetailVO.CinemaBrief buildCinemaBrief(String cinemaId) {
        Cinema c = cinemaMapper.selectById(cinemaId);
        if (c == null) return null;
        return ShowDetailVO.CinemaBrief.builder()
                .cinemaId(c.getCinemaId()).name(c.getName()).address(c.getAddress()).build();
    }

    /** 余座占比阈值：≥40% ample，≥15% tight，否则 almost_full */
    static String calcSeatRemainLevel(int total, int available) {
        if (total <= 0) return "almost_full";
        double ratio = (double) available / total;
        if (ratio >= 0.4) return "ample";
        if (ratio >= 0.15) return "tight";
        return "almost_full";
    }

    /**
     * 根据当前时间计算场次运行时状态。
     * not_started — 未开始（开场时间在未来）；
     * in_progress — 进行中（当前在开场~散场之间）；
     * ended — 已结束（散场时间已过）。
     */
    static String calcRuntimeState(ShowSchedule s) {
        if (s == null || s.getStartTime() == null || s.getEndTime() == null) {
            return null;
        }
        OffsetDateTime now = DateTimeFormats.now();
        if (now.isBefore(s.getStartTime())) {
            return "not_started";
        }
        if (now.isBefore(s.getEndTime())) {
            return "in_progress";
        }
        return "ended";
    }

    @Override
    public PageResult<MovieVO> listMoviesByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime,
                                                     String cinemaId, int page, int size) {
        // 归一化分页
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;
        int offset = (page - 1) * size;

        long total = showMapper.countMoviesByTimeRange(startTime, endTime, cinemaId);
        if (total == 0) {
            return new PageResult<>(Collections.<MovieVO>emptyList(), page, size, 0);
        }

        List<ShowSchedule> rows = showMapper.listMoviesByTimeRange(startTime, endTime, cinemaId, offset, size);
        List<MovieVO> items = new ArrayList<>();
        if (rows != null) {
            for (ShowSchedule row : rows) {
                if (row == null || row.getMovieId() == null) {
                    continue;
                }
                MovieVO vo = buildMovieVO(row.getMovieId());
                if (vo == null) {
                    continue;
                }
                // nextShowDate 使用该时间段内最早开场日期
                if (row.getStartTime() != null) {
                    vo.setNextShowDate(row.getStartTime().atZoneSameInstant(DateTimeFormats.ZONE).toLocalDate().toString());
                }
                items.add(vo);
            }
        }
        return new PageResult<>(items, page, size, total);
    }
}
