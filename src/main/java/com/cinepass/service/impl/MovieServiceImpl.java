package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.MovieCreateDTO;
import com.cinepass.dto.MovieUpdateDTO;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.RecoClickMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.mapper.TagMapper;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
import com.cinepass.service.EsSearchService;
import com.cinepass.service.EsIndexService;
import com.cinepass.service.MovieService;
import com.cinepass.util.MovieIds;
import com.cinepass.util.TagIds;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

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
    private final TagMapper tagMapper;

    public MovieServiceImpl(MovieMapper movieMapper, ShowMapper showMapper,
                            EsSearchService esSearchService, EsIndexService esIndexService,
                            RecoClickMapper recoClickMapper, TagMapper tagMapper) {
        this.movieMapper = movieMapper;
        this.showMapper = showMapper;
        this.esSearchService = esSearchService;
        this.esIndexService = esIndexService;
        this.recoClickMapper = recoClickMapper;
        this.tagMapper = tagMapper;
    }

    // 影片分页搜索：归一化分页参数→有搜索词走ES（含降级到MySQL LIKE），无搜索词走MySQL过滤→补充最近排片日期
    @Override
    public PageResult<MovieVO> page(String status, String q, String genre, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 50) size = 50;

        if (StringUtils.hasText(q)) {
            return searchViaEs(q, status, genre, page, size);
        }

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
    // ES搜索：成功则用MySQL补充posterUrl和排片日期；失败则降级到MySQL LIKE
    private PageResult<MovieVO> searchViaEs(String q, String status, String genre, int page, int size) {
        PageResult<MovieVO> esResult;
        try {
            esResult = esSearchService.searchMovies(q, status, genre, page, size);
        } catch (Exception e) {
            log.warn("ES 搜索失败，降级到 MySQL LIKE: q={}", q, e);
            // 降级到 MySQL LIKE
            long total = movieMapper.countFiltered(status, q, genre);
            List<Movie> rows = movieMapper.listFiltered(status, q, genre, (page - 1) * size, size);
            List<MovieVO> items = new ArrayList<>();
            if (rows != null) {
                for (Movie m : rows) {
                    items.add(toVo(m));
                }
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
        // 收集movieIds，批量从MySQL补充posterUrl和排片日期
        List<String> movieIds = new ArrayList<>();
        for (MovieVO vo : items) {
            if (vo.getMovieId() != null) {
                movieIds.add(vo.getMovieId());
            }
        }
        Map<String, Movie> movieMap = batchGetMovies(movieIds);
        Map<String, String> nextShowMap = batchGetNextShowDates(movieIds);

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

    // 查询影片详情：记录点击（失败不影响返回），转VO
    @Override
    public MovieVO get(String movieId) {
        Movie m = movieMapper.selectById(movieId);
        if (m == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        try {
            recoClickMapper.incrClick(movieId, LocalDate.now(CLICK_ZONE));
        } catch (Exception e) {
            log.warn("记录影片点击失败: {}", e.getMessage());
        }
        return toVo(m);
    }

    // 创建影片：生成ID→装配实体→落库→同步标签字典→同步ES→返回VO
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
        syncTags(dto.getGenres());
        esIndexService.syncMovie(m.getMovieId());
        return toVo(movieMapper.selectById(m.getMovieId()));
    }

    // 更新影片：仅更新非空字段，下架前校验无在售场次，同步标签和ES
    @Override
    @Transactional
    public MovieVO update(String movieId, MovieUpdateDTO dto) {
        Movie m = movieMapper.selectById(movieId);
        if (m == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        // 逐字段非空才更新
        if (dto.getTitle() != null) m.setTitle(dto.getTitle().trim());
        if (dto.getPosterUrl() != null) m.setPosterUrl(dto.getPosterUrl());
        if (dto.getGenres() != null) m.setGenresJson(JSON.toJSONString(dto.getGenres()));
        if (dto.getRating() != null) m.setRating(dto.getRating());
        if (dto.getDurationMin() != null) m.setDurationMin(dto.getDurationMin());
        if (dto.getReleaseDate() != null) m.setReleaseDate(LocalDate.parse(dto.getReleaseDate(), DATE_FMT));
        // 下架前置校验：未来仍有在售场次则阻止
        if ("off".equals(dto.getStatus()) && !"off".equals(m.getStatus())) {
            long onSaleShows = showMapper.countOnSaleByMovie(movieId, OffsetDateTime.now());
            if (onSaleShows > 0) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "该影片还有 " + onSaleShows + " 场在售场次，请先在「场次管理」取消场次后再下架");
            }
        }
        if (dto.getStatus() != null) m.setStatus(dto.getStatus());
        if (dto.getDescription() != null) m.setDescription(dto.getDescription());
        if (dto.getCast() != null) m.setCastText(dto.getCast());
        m.setUpdatedAt(OffsetDateTime.now());
        movieMapper.update(m);
        if (dto.getGenres() != null) {
            syncTags(dto.getGenres());
        }
        esIndexService.syncMovie(movieId);
        return toVo(movieMapper.selectById(movieId));
    }

    // 下架影片：已下架则幂等返回，否则委托update（内部会校验在售场次）
    @Override
    @Transactional
    public MovieVO takeDown(String movieId) {
        Movie m = movieMapper.selectById(movieId);
        if (m == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        if ("off".equals(m.getStatus())) {
            return toVo(m);
        }
        MovieUpdateDTO dto = new MovieUpdateDTO();
        dto.setStatus("off");
        return update(movieId, dto);
    }

    // 重新上架：按上映日期推导状态（已上映→热映，未上映→待映），委托update执行
    @Override
    @Transactional
    public MovieVO relist(String movieId) {
        Movie m = movieMapper.selectById(movieId);
        if (m == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        String target = m.getReleaseDate() != null
                && !m.getReleaseDate().isAfter(LocalDate.now(CLICK_ZONE))
                ? "hot_showing" : "coming_soon";
        MovieUpdateDTO dto = new MovieUpdateDTO();
        dto.setStatus(target);
        return update(movieId, dto);
    }

    @Override
    public List<String> listGenres() {
        List<String> names = tagMapper.listAllNames();
        return names != null ? names : Collections.<String>emptyList();
    }

    /** 影片类型标签字典同步：trim + 去空 + 幂等写入，与建片/改片同一事务 */
    private void syncTags(List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        Set<String> seen = new LinkedHashSet<String>();
        for (String genre : genres) {
            if (genre == null) {
                continue;
            }
            String name = genre.trim();
            if (!name.isEmpty() && seen.add(name)) {
                tagMapper.insertIgnore(TagIds.next(), name, now);
            }
        }
    }

    // Movie实体→MovieVO：解析genres JSON、评分精度处理、解析演职员文本
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
