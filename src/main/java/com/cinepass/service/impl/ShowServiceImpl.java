package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.SeatStatusMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
import com.cinepass.service.ShowService;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.ShowDetailVO;
import com.cinepass.vo.ShowListResult;
import com.cinepass.vo.ShowVO;
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

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    /** 院→片 nextShowDate 展示时区（与产品本地日历日一致） */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    private final ShowMapper showMapper;
    private final SeatStatusMapper seatStatusMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;

    public ShowServiceImpl(ShowMapper showMapper,
                           SeatStatusMapper seatStatusMapper,
                           MovieMapper movieMapper,
                           CinemaMapper cinemaMapper) {
        this.showMapper = showMapper;
        this.seatStatusMapper = seatStatusMapper;
        this.movieMapper = movieMapper;
        this.cinemaMapper = cinemaMapper;
    }

    // 按影院+影片+日期查询场次列表，将实体转为VO后封装日期+列表结果返回
    @Override
    public ShowListResult list(String cinemaId, String movieId, String date) {
        List<ShowSchedule> rows = showMapper.listByMovieCinemaDate(cinemaId, movieId, date);
        List<ShowVO> items = new ArrayList<>();
        if (rows != null) {
            for (ShowSchedule s : rows) {
                items.add(buildShowVO(s));
            }
        }
        return ShowListResult.builder().date(date).items(items).build();
    }

    // 查询某影院某影片的不区分日期的全部场次
    @Override
    public ShowListResult listAll(String cinemaId, String movieId) {
        List<ShowSchedule> rows = showMapper.listByMovieCinema(cinemaId, movieId);
        List<ShowVO> items = new ArrayList<>();
        if (rows != null) {
            for (ShowSchedule s : rows) {
                items.add(buildShowVO(s));
            }
        }
        return ShowListResult.builder().date(null).items(items).build();
    }

    // 查某影院当前在售影片列表：校验影院存在→查询每个影片的最早未来场次→组装VO并附带最近排片日
    @Override
    public PageResult<MovieVO> listOnSaleMovies(String cinemaId) {
        if (cinemaMapper.selectById(cinemaId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影院不存在");
        }
        OffsetDateTime after = OffsetDateTime.now();
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
                vo.setNextShowDate(row.getStartTime().atZoneSameInstant(DISPLAY_ZONE).toLocalDate().toString());
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
        return buildShowVO(s, null);
    }

    // 场次实体→VO：统计余座、计算余座等级、格式化时间、组装分区价格
    @Override
    public ShowVO buildShowVO(ShowSchedule s, List<ShowVO.ZonePriceVO> zonePrices) {
        int total = seatStatusMapper.countTotal(s.getShowId());
        int available = seatStatusMapper.countAvailable(s.getShowId());
        String level = calcSeatRemainLevel(total, available);
        return ShowVO.builder()
                .showId(s.getShowId()).movieId(s.getMovieId())
                .cinemaId(s.getCinemaId()).hallId(s.getHallId())
                .hallName(s.getHallName())
                .startTime(s.getStartTime() != null ? FMT.format(s.getStartTime()) : null)
                .endTime(s.getEndTime() != null ? FMT.format(s.getEndTime()) : null)
                .price(s.getPrice())
                .zonePrices(zonePrices)
                .seatRemain(available).seatRemainLevel(level)
                .status(s.getStatus()).build();
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

    // 按时间范围+影院分页查询有排片的影片：归一化分页参数→查总数→分页查场次→组装VO并附最近排片日
    @Override
    public PageResult<MovieVO> listMoviesByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime,
                                                     String cinemaId, int page, int size) {
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
                if (row.getStartTime() != null) {
                    vo.setNextShowDate(row.getStartTime().atZoneSameInstant(DISPLAY_ZONE).toLocalDate().toString());
                }
                items.add(vo);
            }
        }
        return new PageResult<>(items, page, size, total);
    }
}
