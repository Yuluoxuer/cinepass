package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.cache.RecoCacheService;
import com.cinepass.constant.RecoConstants;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.OrderTicketMapper;
import com.cinepass.mapper.RecoStatsMapper;
import com.cinepass.mapper.RecoWeightMapper;
import com.cinepass.mapper.UserProfileMapper;
import com.cinepass.mapper.WantSeeMapper;
import com.cinepass.model.Movie;
import com.cinepass.model.RecoStats;
import com.cinepass.model.RecoWeight;
import com.cinepass.model.UserProfile;
import com.cinepass.service.RecoService;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.RecoBaseVO;
import com.cinepass.vo.PersonalRecoItemVO;
import com.cinepass.vo.PersonalRecoVO;
import com.cinepass.vo.WeeklyHotItemVO;
import com.cinepass.vo.WeeklyHotVO;
import com.cinepass.util.DateTimeFormats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link RecoService} 实现。
 * <p>每周热门：reco_stats 物化周信号，热门分 = 城市权重 × 归一化信号；GET 时按城市权重重算保证权重即时生效。
 * 个人推荐：显式偏好（user_profile）做类型匹配、行为偏好（想看 ∪ 已购）做标签匹配，加权打分后做同类型连续 ≤3 贪心排布。</p>
 */
@Slf4j
@Service
public class RecoServiceImpl implements RecoService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final RecoStatsMapper recoStatsMapper;
    private final RecoWeightMapper recoWeightMapper;
    private final MovieMapper movieMapper;
    private final UserProfileMapper userProfileMapper;
    private final WantSeeMapper wantSeeMapper;
    private final OrderTicketMapper orderTicketMapper;
    private final RecoCacheService recoCacheService;
    private final TransactionTemplate tx;

    public RecoServiceImpl(RecoStatsMapper recoStatsMapper,
                           RecoWeightMapper recoWeightMapper,
                           MovieMapper movieMapper,
                           UserProfileMapper userProfileMapper,
                           WantSeeMapper wantSeeMapper,
                           OrderTicketMapper orderTicketMapper,
                           RecoCacheService recoCacheService,
                           PlatformTransactionManager txManager) {
        this.recoStatsMapper = recoStatsMapper;
        this.recoWeightMapper = recoWeightMapper;
        this.movieMapper = movieMapper;
        this.userProfileMapper = userProfileMapper;
        this.wantSeeMapper = wantSeeMapper;
        this.orderTicketMapper = orderTicketMapper;
        this.recoCacheService = recoCacheService;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public WeeklyHotVO weeklyHot(String cityId, Integer limit) {
        RecoBaseVO base = base();
        String city = StringUtils.hasText(cityId) ? cityId.trim() : RecoConstants.DEFAULT_CITY;
        RecoWeight weight = recoWeightMapper.selectByCity(city);
        if (weight == null) {
            // 幂等 seed 默认权重后重读；读不到仍回退常量默认值
            recoWeightMapper.insertDefault(city, DateTimeFormats.now());
            weight = recoWeightMapper.selectByCity(city);
        }

        List<Ranked> ranked = rankByCityWeights(base, cityWeights(weight));
        if (ranked.isEmpty()) {
            return WeeklyHotVO.builder()
                    .computedAt(DateTimeFormats.format(DateTimeFormats.now()))
                    .items(Collections.<WeeklyHotItemVO>emptyList())
                    .build();
        }

        int n = clamp(limit == null ? 10 : limit.intValue(), 1, RecoConstants.WEEKLY_HOT_MAX);
        int take = Math.min(n, ranked.size());
        List<WeeklyHotItemVO> items = new ArrayList<WeeklyHotItemVO>();
        for (int i = 0; i < take; i++) {
            Ranked r = ranked.get(i);
            items.add(WeeklyHotItemVO.builder()
                    .rank(i + 1)
                    .hotScore(r.score)
                    .heatTag(heatTag(i + 1))
                    .movie(r.movie)
                    .build());
        }
        return WeeklyHotVO.builder()
                .computedAt(base.getComputedAt())
                .items(items)
                .build();
    }

    @Override
    public PersonalRecoVO personal(String userId, Integer limit, String excludeMovieIds) {
        int n = clamp(limit == null ? 10 : limit.intValue(), 1, RecoConstants.PERSONAL_MAX);
        Set<String> exclude = parseExclude(excludeMovieIds);
        if (!StringUtils.hasText(userId)) {
            return fallbackHot(n, exclude);
        }

        RecoBaseVO base = base();
        Set<String> prefGenres = explicitPrefs(userId);
        Set<String> behaviorGenres = behaviorGenres(userId);

        List<PersonalRecoItemVO> scored = new ArrayList<PersonalRecoItemVO>();
        for (Map.Entry<String, MovieVO> e : base.getMovies().entrySet()) {
            String movieId = e.getKey();
            if (exclude.contains(movieId)) {
                continue;
            }
            MovieVO m = e.getValue();
            RecoStats signal = base.getSignals().get(movieId);
            List<String> genres = m.getGenres() == null ? Collections.<String>emptyList() : m.getGenres();
            double genreMatch = jaccard(genres, prefGenres);
            double tagMatch = jaccard(genres, behaviorGenres);
            double ratingNorm = ratingNorm(m.getRating()).doubleValue();
            double hotNorm = hotNorm(signal);
            double score = 0.5 * genreMatch + 0.2 * tagMatch + 0.2 * ratingNorm + 0.1 * hotNorm;
            scored.add(PersonalRecoItemVO.builder()
                    .movie(m)
                    .personalScore(BigDecimal.valueOf(clamp(score, 0, 1)).setScale(4, RoundingMode.HALF_UP))
                    .reason(reason(genreMatch, ratingNorm, hotNorm, genres, prefGenres))
                    .build());
        }
        Collections.sort(scored, new Comparator<PersonalRecoItemVO>() {
            @Override
            public int compare(PersonalRecoItemVO a, PersonalRecoItemVO b) {
                return b.getPersonalScore().compareTo(a.getPersonalScore());
            }
        });
        return PersonalRecoVO.builder()
                .mode(RecoConstants.MODE_PERSONALIZED)
                .items(enforceConsecutiveGenreLimit(scored, n))
                .build();
    }

    @Override
    public void recomputeStats() {
        OffsetDateTime now = DateTimeFormats.now();
        List<Movie> movies = movieMapper.listAll();
        if (movies == null || movies.isEmpty()) {
            return;
        }
        Map<String, Integer> orders = orderCountMap(recoStatsMapper.selectWeekOrderCounts(now.minusDays(7)));
        Map<String, Integer> clicks = clickCountMap(recoStatsMapper.selectWeekClickCounts(LocalDate.now(DateTimeFormats.ZONE).minusDays(7)));

        List<RecoStats> rows = new ArrayList<RecoStats>();
        for (Movie m : movies) {
            // 下映片不进榜单；待映/热映参与打分
            if (m.getMovieId() == null || "off".equals(m.getStatus())) {
                continue;
            }
            RecoStats r = new RecoStats();
            r.setMovieId(m.getMovieId());
            r.setWeekOrders(orZero(orders.get(m.getMovieId())));
            r.setWeekClicks(orZero(clicks.get(m.getMovieId())));
            r.setRatingNorm(ratingNorm(m.getRating()));
            r.setFreshness(freshness(m.getReleaseDate(), m.getStatus()));
            r.setHotScore(BigDecimal.ZERO);
            rows.add(r);
        }
        if (rows.isEmpty()) {
            return;
        }
        // 物化热门分用默认城市权重；GET 按请求城市权重重算
        fillHotScore(rows, RecoConstants.W_ORDERS, RecoConstants.W_CLICKS,
                RecoConstants.W_RATING, RecoConstants.W_FRESH);
        for (RecoStats r : rows) {
            r.setComputedAt(now);
        }
        final List<RecoStats> finalRows = rows;
        // 事务内全量重写：避免 deleteAll 成功、批量插入失败导致空榜半写
        tx.executeWithoutResult(status -> {
            recoStatsMapper.deleteAll();
            recoStatsMapper.batchInsert(finalRows);
        });
        // 顺带重建推荐底座缓存（用内存数据，避免再查库）；失败仅记日志，下轮调度/读触发重建
        try {
            recoCacheService.writeBase(toBase(rows, movies));
        } catch (Exception e) {
            log.warn("重建推荐底座缓存失败: {}", e.getMessage());
        }
    }

    /** reco_stats 为空或物化超过 1 小时则同步重算，保证首次/长时间未调度也能出榜 */
    private void ensureFreshStats() {
        long count = recoStatsMapper.count();
        OffsetDateTime max = recoStatsMapper.selectMaxComputedAt();
        if (count == 0 || max == null
                || System.currentTimeMillis() - max.toInstant().toEpochMilli() > RecoConstants.STATS_STALE_MS) {
            recomputeStats();
        }
    }

    /** 按城市权重对原始信号打分并写回 hotScore（0–100，保留 1 位） */
    private void fillHotScore(List<RecoStats> rows, BigDecimal wOrders, BigDecimal wClicks,
                              BigDecimal wRating, BigDecimal wFresh) {
        int maxOrders = 0;
        int maxClicks = 0;
        for (RecoStats r : rows) {
            maxOrders = Math.max(maxOrders, orZero(r.getWeekOrders()));
            maxClicks = Math.max(maxClicks, orZero(r.getWeekClicks()));
        }
        for (RecoStats r : rows) {
            double nOrders = maxOrders > 0 ? (double) orZero(r.getWeekOrders()) / maxOrders : 0;
            double nClicks = maxClicks > 0 ? (double) orZero(r.getWeekClicks()) / maxClicks : 0;
            double rating = r.getRatingNorm() == null ? 0 : r.getRatingNorm().doubleValue();
            double fresh = r.getFreshness() == null ? 0 : r.getFreshness().doubleValue();
            double hot = 100 * (d(wOrders) * nOrders + d(wClicks) * nClicks
                    + d(wRating) * rating + d(wFresh) * fresh);
            r.setHotScore(BigDecimal.valueOf(clamp(hot, 0, 100)).setScale(1, RoundingMode.HALF_UP));
        }
    }

    /** 城市权重提取；行或某字段缺失时回退默认值 */
    private BigDecimal[] cityWeights(RecoWeight w) {
        return new BigDecimal[]{
                w != null && w.getWOrders() != null ? w.getWOrders() : RecoConstants.W_ORDERS,
                w != null && w.getWClicks() != null ? w.getWClicks() : RecoConstants.W_CLICKS,
                w != null && w.getWRating() != null ? w.getWRating() : RecoConstants.W_RATING,
                w != null && w.getWFresh() != null ? w.getWFresh() : RecoConstants.W_FRESH
        };
    }

    /** 评分归一化：评分/10，clamp 0–1 */
    private BigDecimal ratingNorm(BigDecimal rating) {
        if (rating == null) {
            return ZERO;
        }
        BigDecimal v = rating.divide(new BigDecimal("10"), 4, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(clamp(v.doubleValue(), 0, 1)).setScale(4, RoundingMode.HALF_UP);
    }

    /** 新鲜度 0–1：已上映按上映天数 90 天衰减；待映按距上映 30 天内递增；其余 0 */
    private BigDecimal freshness(LocalDate releaseDate, String status) {
        if (releaseDate == null) {
            return ZERO;
        }
        LocalDate today = LocalDate.now(DateTimeFormats.ZONE);
        double f;
        if ("hot_showing".equals(status)) {
            long days = ChronoUnit.DAYS.between(releaseDate, today);
            f = 1.0 - (double) days / RecoConstants.SHOWING_FRESH_WINDOW_DAYS;
        } else if ("coming_soon".equals(status)) {
            long days = ChronoUnit.DAYS.between(today, releaseDate);
            f = 1.0 - (double) days / RecoConstants.COMING_SOON_FRESH_WINDOW_DAYS;
        } else {
            return ZERO;
        }
        return BigDecimal.valueOf(clamp(f, 0, 1)).setScale(4, RoundingMode.HALF_UP);
    }

    /** 未登录：复用热门榜作为回退 */
    private PersonalRecoVO fallbackHot(int limit, Set<String> exclude) {
        WeeklyHotVO hot = weeklyHot(RecoConstants.DEFAULT_CITY,
                Math.min(limit + exclude.size(), RecoConstants.WEEKLY_HOT_MAX));
        List<PersonalRecoItemVO> items = new ArrayList<PersonalRecoItemVO>();
        if (hot.getItems() != null) {
            for (WeeklyHotItemVO h : hot.getItems()) {
                MovieVO m = h.getMovie();
                if (m == null || m.getMovieId() == null || exclude.contains(m.getMovieId())) {
                    continue;
                }
                items.add(PersonalRecoItemVO.builder()
                        .movie(m)
                        .personalScore(h.getHotScore() == null ? null
                                : h.getHotScore().divide(HUNDRED, 4, RoundingMode.HALF_UP))
                        .reason("本周热门")
                        .build());
                if (items.size() >= limit) {
                    break;
                }
            }
        }
        return PersonalRecoVO.builder().mode(RecoConstants.MODE_FALLBACK_HOT).items(items).build();
    }

    /** 显式偏好：user_profile.prefer_genres_json */
    private Set<String> explicitPrefs(String userId) {
        UserProfile p = userProfileMapper.findById(userId);
        if (p == null || !StringUtils.hasText(p.getPreferGenresJson())) {
            return Collections.emptySet();
        }
        List<String> list = JSON.parseArray(p.getPreferGenresJson(), String.class);
        return list == null ? Collections.<String>emptySet() : new HashSet<String>(list);
    }

    /** 行为偏好：想看 ∪ 已购影片的类型集合 */
    private Set<String> behaviorGenres(String userId) {
        Set<String> ids = new HashSet<String>();
        List<String> wantSee = wantSeeMapper.listAllMovieIds(userId);
        List<String> purchased = orderTicketMapper.selectIssuedMovieIdsByUser(userId);
        if (wantSee != null) {
            ids.addAll(wantSee);
        }
        if (purchased != null) {
            ids.addAll(purchased);
        }
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> genres = new HashSet<String>();
        List<Movie> movies = movieMapper.selectByIds(new ArrayList<String>(ids));
        if (movies != null) {
            for (Movie m : movies) {
                genres.addAll(genresOf(m));
            }
        }
        return genres;
    }

    /** 读推荐底座缓存；Redis 故障/无缓存时由缓存组件内部降级为 DB 现算 */
    private RecoBaseVO base() {
        return recoCacheService.getOrRefresh(this::computeAndCache);
    }

    /** 缓存刷新加载器：确保 reco_stats 新鲜后从 DB 构建底座 */
    private RecoBaseVO computeAndCache() {
        ensureFreshStats();
        return buildBaseFromDb();
    }

    private RecoBaseVO buildBaseFromDb() {
        return toBase(recoStatsMapper.selectAll(), movieMapper.listAll());
    }

    /** 信号 + 影片 → 底座 VO（只含 hot_showing + coming_soon） */
    private RecoBaseVO toBase(List<RecoStats> stats, List<Movie> movies) {
        Map<String, RecoStats> signals = new HashMap<String, RecoStats>();
        if (stats != null) {
            for (RecoStats r : stats) {
                if (r.getMovieId() != null) {
                    signals.put(r.getMovieId(), r);
                }
            }
        }
        Map<String, MovieVO> vos = new HashMap<String, MovieVO>();
        if (movies != null) {
            for (Movie m : movies) {
                if (m.getMovieId() == null || "off".equals(m.getStatus())) {
                    continue;
                }
                vos.put(m.getMovieId(), toMovieVO(m));
            }
        }
        return RecoBaseVO.builder().computedAt(computedAt(stats)).signals(signals).movies(vos).build();
    }

    /** 取 reco_stats 最大计算时间；无则当前时间 */
    private String computedAt(List<RecoStats> stats) {
        OffsetDateTime max = null;
        if (stats != null) {
            for (RecoStats r : stats) {
                if (r.getComputedAt() != null && (max == null || r.getComputedAt().isAfter(max))) {
                    max = r.getComputedAt();
                }
            }
        }
        return DateTimeFormats.format(max != null ? max : DateTimeFormats.now());
    }

    /** 按城市权重对底座信号打分排序（只读缓存对象，不修改） */
    private List<Ranked> rankByCityWeights(RecoBaseVO base, BigDecimal[] w) {
        List<RecoStats> signals = new ArrayList<RecoStats>(base.getSignals().values());
        int maxOrders = 0;
        int maxClicks = 0;
        for (RecoStats r : signals) {
            maxOrders = Math.max(maxOrders, orZero(r.getWeekOrders()));
            maxClicks = Math.max(maxClicks, orZero(r.getWeekClicks()));
        }
        List<Ranked> out = new ArrayList<Ranked>();
        for (RecoStats r : signals) {
            MovieVO m = base.getMovies().get(r.getMovieId());
            if (m == null) {
                continue;
            }
            double nOrders = maxOrders > 0 ? (double) orZero(r.getWeekOrders()) / maxOrders : 0;
            double nClicks = maxClicks > 0 ? (double) orZero(r.getWeekClicks()) / maxClicks : 0;
            double rating = r.getRatingNorm() == null ? 0 : r.getRatingNorm().doubleValue();
            double fresh = r.getFreshness() == null ? 0 : r.getFreshness().doubleValue();
            double hot = 100 * (d(w[0]) * nOrders + d(w[1]) * nClicks
                    + d(w[2]) * rating + d(w[3]) * fresh);
            out.add(new Ranked(m, BigDecimal.valueOf(clamp(hot, 0, 100)).setScale(1, RoundingMode.HALF_UP)));
        }
        // 同分按 movieId 做确定性次级排序，避免依赖 HashMap 桶序
        out.sort((a, b) -> {
            int c = b.score.compareTo(a.score);
            return c != 0 ? c : a.movie.getMovieId().compareTo(b.movie.getMovieId());
        });
        return out;
    }

    /** 城市权重打分中间结果（避免修改缓存中的 RecoStats） */
    private static class Ranked {
        final MovieVO movie;
        final BigDecimal score;

        Ranked(MovieVO movie, BigDecimal score) {
            this.movie = movie;
            this.score = score;
        }
    }

    /** 热门分归一化到 0–1；无物化行按 0 */
    private double hotNorm(RecoStats r) {
        if (r == null || r.getHotScore() == null) {
            return 0;
        }
        return clamp(r.getHotScore().doubleValue() / 100, 0, 1);
    }

    /** Jaccard 相似度：候选类型与偏好集合的交/并；任一侧为空按 0 */
    private double jaccard(List<String> a, Set<String> b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) {
            return 0;
        }
        Set<String> union = new HashSet<String>(b);
        int inter = 0;
        for (String g : a) {
            if (b.contains(g)) {
                inter++;
            }
            union.add(g);
        }
        return (double) inter / union.size();
    }

    /** 推荐理由：类型命中 > 高分口碑 > 本周热门 > null */
    private String reason(double genreMatch, double ratingNorm, double hotNorm,
                          List<String> genres, Set<String> prefGenres) {
        if (genreMatch >= 0.5) {
            if (genres != null && prefGenres != null) {
                for (String g : genres) {
                    if (prefGenres.contains(g)) {
                        return "符合你偏好的" + g;
                    }
                }
            }
            return "符合你的观影偏好";
        }
        if (ratingNorm >= 0.85) {
            return "口碑佳作";
        }
        if (hotNorm >= 0.7) {
            return "本周热门";
        }
        return null;
    }

    /** 同类型连续 ≤3：按分数降序贪心排布，超限顺延到末尾（系分 §4.2） */
    private List<PersonalRecoItemVO> enforceConsecutiveGenreLimit(List<PersonalRecoItemVO> sorted, int limit) {
        List<PersonalRecoItemVO> out = new ArrayList<PersonalRecoItemVO>();
        List<PersonalRecoItemVO> deferred = new ArrayList<PersonalRecoItemVO>();
        String lastGenre = null;
        int run = 0;
        for (PersonalRecoItemVO item : sorted) {
            String g = primaryGenre(item.getMovie());
            if (g != null && g.equals(lastGenre) && run >= RecoConstants.MAX_SAME_GENRE_RUN) {
                deferred.add(item);
                continue;
            }
            out.add(item);
            if (g != null && g.equals(lastGenre)) {
                run++;
            } else {
                run = 1;
                lastGenre = g;
            }
        }
        out.addAll(deferred);
        return out.size() > limit ? new ArrayList<PersonalRecoItemVO>(out.subList(0, limit)) : out;
    }

    /** 影片主类型：genres 首个；无则 null */
    private String primaryGenre(MovieVO movie) {
        if (movie == null || movie.getGenres() == null || movie.getGenres().isEmpty()) {
            return null;
        }
        return movie.getGenres().get(0);
    }

    /** 热度标签：1=本周爆款，2–3=人气佳作，其余=正在热映 */
    private String heatTag(int rank) {
        if (rank == 1) {
            return "本周爆款";
        }
        if (rank <= 3) {
            return "人气佳作";
        }
        return "正在热映";
    }

    /** 逗号分隔排除列表 → Set */
    private Set<String> parseExclude(String excludeMovieIds) {
        if (!StringUtils.hasText(excludeMovieIds)) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<String>();
        for (String s : excludeMovieIds.split(",")) {
            if (StringUtils.hasText(s)) {
                set.add(s.trim());
            }
        }
        return set;
    }

    /** 订单计数聚合 → movieId → 计数值（字段 weekOrders） */
    private Map<String, Integer> orderCountMap(List<RecoStats> rows) {
        Map<String, Integer> map = new HashMap<String, Integer>();
        if (rows != null) {
            for (RecoStats r : rows) {
                map.put(r.getMovieId(), orZero(r.getWeekOrders()));
            }
        }
        return map;
    }

    /** 点击计数聚合 → movieId → 计数值（字段 weekClicks） */
    private Map<String, Integer> clickCountMap(List<RecoStats> rows) {
        Map<String, Integer> map = new HashMap<String, Integer>();
        if (rows != null) {
            for (RecoStats r : rows) {
                map.put(r.getMovieId(), orZero(r.getWeekClicks()));
            }
        }
        return map;
    }

    private int orZero(Integer v) {
        return v == null ? 0 : v.intValue();
    }

    private double d(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private List<String> genresOf(Movie m) {
        return m.getGenresJson() == null ? Collections.<String>emptyList()
                : JSON.parseArray(m.getGenresJson(), String.class);
    }

    /** Movie → MovieVO；genres_json 解析为列表（与 WantSeeServiceImpl 一致） */
    private MovieVO toMovieVO(Movie m) {
        List<String> genres = genresOf(m);
        BigDecimal rating = m.getRating();
        if (rating != null) {
            rating = rating.setScale(1, RoundingMode.HALF_UP);
        }
        return MovieVO.builder()
                .movieId(m.getMovieId())
                .title(m.getTitle())
                .posterUrl(m.getPosterUrl())
                .genres(genres)
                .rating(rating)
                .durationMin(m.getDurationMin())
                .releaseDate(m.getReleaseDate() != null ? m.getReleaseDate().toString() : null)
                .status(m.getStatus())
                .description(m.getDescription())
                .cast(m.getCastText())
                .wantSeeCount(m.getWantSeeCount())
                .build();
    }
}
