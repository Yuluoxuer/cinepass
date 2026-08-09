package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.MovieCreateDTO;
import com.cinepass.dto.MovieUpdateDTO;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.RecoClickMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
import com.cinepass.service.EsSearchService;
import com.cinepass.service.EsIndexService;
import com.cinepass.service.MovieService;
import com.cinepass.util.MovieIds;
import com.cinepass.vo.CastMemberVO;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * {@link MovieService} 实现。
 * <p>影片搜索：有搜索词 q 时走 ES 全文检索（multi_match），无 q 时走 MySQL 过滤。
 */
@Service
public class MovieServiceImpl implements MovieService {

    private static final Logger log = LoggerFactory.getLogger(MovieServiceImpl.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    /** 点击计数按本地日历日切分 */
    private static final ZoneId CLICK_ZONE = ZoneId.of("Asia/Shanghai");

    private final MovieMapper movieMapper;
    private final ShowMapper showMapper;
    private final EsSearchService esSearchService;
    private final EsIndexService esIndexService;
    private final RecoClickMapper recoClickMapper;

    public MovieServiceImpl(MovieMapper movieMapper, ShowMapper showMapper,
                            EsSearchService esSearchService, EsIndexService esIndexService,
                            RecoClickMapper recoClickMapper) {
        this.movieMapper = movieMapper;
        this.showMapper = showMapper;
        this.esSearchService = esSearchService;
        this.esIndexService = esIndexService;
        this.recoClickMapper = recoClickMapper;
    }

    @Override
    public PageResult<MovieVO> page(String status, String q, String genre, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 50) size = 50;

        // 有搜索关键词时走 ES 全文检索
        if (StringUtils.hasText(q)) {
            return searchViaEs(q, status, genre, page, size);
        }

        // 无搜索词时走 MySQL 简单过滤
        long total = movieMapper.countFiltered(status, q, genre);
        List<Movie> rows = movieMapper.listFiltered(status, q, genre, (page - 1) * size, size);
        List<MovieVO> items = new ArrayList<>();
        if (rows != null) {
            for (Movie m : rows) {
                items.add(toVo(m));
            }
            // 补充最近排片日期
            List<String> movieIds = new ArrayList<>();
            for (MovieVO vo : items) {
                if (vo.getMovieId() != null) {
                    movieIds.add(vo.getMovieId());
                }
            }
            Map<String, String> nextShowMap = batchGetNextShowDates(movieIds);
            for (MovieVO vo : items) {
                vo.setNextShowDate(nextShowMap.get(vo.getMovieId()));
            }
        }
        return new PageResult<>(items, page, size, total);
    }

    /** ES 搜索 + MySQL 字段补充（posterUrl、nextShowDate） */
    private PageResult<MovieVO> searchViaEs(String q, String status, String genre, int page, int size) {
        PageResult<MovieVO> esResult;
        try {
            esResult = esSearchService.searchMovies(q, status, genre, page, size);
        } catch (Exception e) {
            log.warn("ES 搜索失败，降级到 MySQL LIKE: q={}", q, e);
            // 降级：走 MySQL LIKE
            long total = movieMapper.countFiltered(status, q, genre);
            List<Movie> rows = movieMapper.listFiltered(status, q, genre, (page - 1) * size, size);
            List<MovieVO> items = new ArrayList<>();
            if (rows != null) {
                for (Movie m : rows) {
                    items.add(toVo(m));
                }
                // 补充最近排片日期
                List<String> movieIds = new ArrayList<>();
                for (MovieVO vo : items) {
                    if (vo.getMovieId() != null) {
                        movieIds.add(vo.getMovieId());
                    }
                }
                Map<String, String> nextShowMap = batchGetNextShowDates(movieIds);
                for (MovieVO vo : items) {
                    vo.setNextShowDate(nextShowMap.get(vo.getMovieId()));
                }
            }
            return new PageResult<>(items, page, size, total);
        }

        List<MovieVO> items = esResult.getItems();
        if (items == null || items.isEmpty()) {
            return esResult;
        }

        // 收集 movieId 列表用于 MySQL 补充字段
        List<String> movieIds = new ArrayList<>();
        for (MovieVO vo : items) {
            if (vo.getMovieId() != null) {
                movieIds.add(vo.getMovieId());
            }
        }

        // 批量查 MySQL 补充 posterUrl
        Map<String, Movie> movieMap = batchGetMovies(movieIds);

        // 批量查最近排片日期
        Map<String, String> nextShowMap = batchGetNextShowDates(movieIds);

        // 合并补充字段
        for (MovieVO vo : items) {
            if (vo.getMovieId() == null) continue;
            Movie dbMovie = movieMap.get(vo.getMovieId());
            if (dbMovie != null) {
                if (vo.getPosterUrl() == null) {
                    vo.setPosterUrl(dbMovie.getPosterUrl());
                }
            }
            vo.setNextShowDate(nextShowMap.get(vo.getMovieId()));
        }

        return esResult;
    }

    /** 批量查询影片，返回 movieId → Movie 映射 */
    private Map<String, Movie> batchGetMovies(List<String> movieIds) {
        if (movieIds.isEmpty()) return Collections.emptyMap();
        List<Movie> movies = movieMapper.selectByIds(movieIds);
        Map<String, Movie> map = new HashMap<>();
        if (movies != null) {
            for (Movie m : movies) {
                map.put(m.getMovieId(), m);
            }
        }
        return map;
    }

    /** 批量查询影片最近排片日期，返回 movieId → yyyy-MM-dd 映射（统一东八区，避免 JVM 默认时区偏移一天） */
    private Map<String, String> batchGetNextShowDates(List<String> movieIds) {
        if (movieIds.isEmpty()) return Collections.emptyMap();
        List<ShowSchedule> rows = showMapper.listEarliestByMovieIds(movieIds, OffsetDateTime.now());
        Map<String, String> map = new HashMap<>();
        if (rows != null) {
            for (ShowSchedule s : rows) {
                if (s.getMovieId() != null && s.getStartTime() != null) {
                    map.put(s.getMovieId(),
                            s.getStartTime().atZoneSameInstant(CLICK_ZONE).toLocalDate().toString());
                }
            }
        }
        return map;
    }

    @Override
    public MovieVO get(String movieId) {
        Movie m = movieMapper.selectById(movieId);
        if (m == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        // 详情访问即记当日点击，供每周热门 week_clicks 统计（系分 §3.6）；失败不影响详情返回
        try {
            recoClickMapper.incrClick(movieId, LocalDate.now(CLICK_ZONE));
        } catch (Exception e) {
            log.warn("记录影片点击失败: {}", e.getMessage());
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
        esIndexService.syncMovie(m.getMovieId());
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
        esIndexService.syncMovie(movieId);
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
                .cast(m.getCastText())
                .castMembers(parseCastMembers(m.getCastText()))
                .wantSeeCount(m.getWantSeeCount())
                .build();
    }

    /**
     * 将逗号/斜杠分隔的演职员文本解析为结构化 CastMemberVO 列表。
     */
    private static List<CastMemberVO> parseCastMembers(String castText) {
        if (!StringUtils.hasText(castText)) {
            return Collections.emptyList();
        }
        List<CastMemberVO> result = new ArrayList<>();
        String[] parts = castText.split("\\s*[/、,，]\\s*");
        for (String part : parts) {
            String name = part.trim();
            if (!name.isEmpty()) {
                result.add(CastMemberVO.builder().name(name).build());
            }
        }
        return result;
    }
}
