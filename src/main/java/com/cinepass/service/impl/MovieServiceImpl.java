package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.MovieCreateDTO;
import com.cinepass.dto.MovieUpdateDTO;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.model.Movie;
import com.cinepass.service.MovieService;
import com.cinepass.util.MovieIds;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MovieServiceImpl implements MovieService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MovieMapper movieMapper;

    public MovieServiceImpl(MovieMapper movieMapper) {
        this.movieMapper = movieMapper;
    }

    @Override
    public PageResult<MovieVO> page(String status, String q, String genre, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 50) size = 50;
        long total = movieMapper.countFiltered(status, q, genre);
        List<Movie> rows = movieMapper.listFiltered(status, q, genre, (page - 1) * size, size);
        List<MovieVO> items = new ArrayList<>();
        if (rows != null) {
            for (Movie m : rows) {
                items.add(toVo(m));
            }
        }
        return new PageResult<>(items, page, size, total);
    }

    @Override
    public MovieVO get(String movieId) {
        Movie m = movieMapper.selectById(movieId);
        if (m == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        return toVo(m);
    }

    @Override
    @Transactional
    public MovieVO create(MovieCreateDTO dto) {
        OffsetDateTime now = OffsetDateTime.now();
        Movie m = new Movie();
        m.setMovieId(MovieIds.next());
        m.setTitle(dto.getTitle().trim());
        m.setPosterUrl(dto.getPosterUrl());
        m.setGenresJson(JSON.toJSONString(dto.getGenres()));
        m.setRating(dto.getRating());
        m.setDurationMin(dto.getDurationMin());
        m.setReleaseDate(LocalDate.parse(dto.getReleaseDate(), DATE_FMT));
        m.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "coming_soon");
        m.setDescription(dto.getDescription());
        m.setCastText(StringUtils.hasText(dto.getCast()) ? dto.getCast() : null);
        m.setWantSeeCount(0);
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        movieMapper.insert(m);
        return toVo(movieMapper.selectById(m.getMovieId()));
    }

    @Override
    @Transactional
    public MovieVO update(String movieId, MovieUpdateDTO dto) {
        Movie m = movieMapper.selectById(movieId);
        if (m == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        if (dto.getTitle() != null) m.setTitle(dto.getTitle().trim());
        if (dto.getPosterUrl() != null) m.setPosterUrl(dto.getPosterUrl());
        if (dto.getGenres() != null) m.setGenresJson(JSON.toJSONString(dto.getGenres()));
        if (dto.getRating() != null) m.setRating(dto.getRating());
        if (dto.getDurationMin() != null) m.setDurationMin(dto.getDurationMin());
        if (dto.getReleaseDate() != null) m.setReleaseDate(LocalDate.parse(dto.getReleaseDate(), DATE_FMT));
        if (dto.getStatus() != null) m.setStatus(dto.getStatus());
        if (dto.getDescription() != null) m.setDescription(dto.getDescription());
        if (dto.getCast() != null) m.setCastText(dto.getCast());
        m.setUpdatedAt(OffsetDateTime.now());
        movieMapper.update(m);
        return toVo(movieMapper.selectById(movieId));
    }

    private MovieVO toVo(Movie m) {
        List<String> genres = m.getGenresJson() == null
                ? Collections.<String>emptyList()
                : JSON.parseArray(m.getGenresJson(), String.class);
        BigDecimal rating = m.getRating();
        if (rating != null) {
            rating = rating.setScale(1, BigDecimal.ROUND_HALF_UP);
        }
        return MovieVO.builder()
                .movieId(m.getMovieId()).title(m.getTitle()).posterUrl(m.getPosterUrl())
                .genres(genres).rating(rating).durationMin(m.getDurationMin())
                .releaseDate(m.getReleaseDate() != null ? m.getReleaseDate().toString() : null)
                .status(m.getStatus()).description(m.getDescription())
                .cast(m.getCastText()).wantSeeCount(m.getWantSeeCount())
                .build();
    }
}
