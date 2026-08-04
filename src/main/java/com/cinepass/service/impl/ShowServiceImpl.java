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
import com.cinepass.vo.ShowDetailVO;
import com.cinepass.vo.ShowListResult;
import com.cinepass.vo.ShowVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ShowServiceImpl implements ShowService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

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

    private ShowDetailVO.CinemaBrief buildCinemaBrief(String cinemaId) {
        Cinema c = cinemaMapper.selectById(cinemaId);
        if (c == null) return null;
        return ShowDetailVO.CinemaBrief.builder()
                .cinemaId(c.getCinemaId()).name(c.getName()).address(c.getAddress()).build();
    }

    static String calcSeatRemainLevel(int total, int available) {
        if (total <= 0) return "almost_full";
        double ratio = (double) available / total;
        if (ratio >= 0.4) return "ample";
        if (ratio >= 0.15) return "tight";
        return "almost_full";
    }
}
