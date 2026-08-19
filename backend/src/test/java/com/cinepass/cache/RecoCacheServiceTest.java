package com.cinepass.cache;

import com.alibaba.fastjson2.JSON;
import com.cinepass.model.RecoStats;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.RecoBaseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link RecoCacheService}（Redisson 版）单元测试：命中/单飞赢家/输家降级/失效/序列化往返。 */
class RecoCacheServiceTest {

    private RBucket<Object> bucket;
    private RLock lock;
    private RecoCacheService cacheService;

    @BeforeEach
    void setUp() {
        RedissonClient redisson = mock(RedissonClient.class);
        bucket = mock(RBucket.class);
        lock = mock(RLock.class);
        when(redisson.getBucket(anyString(), any())).thenReturn(bucket);
        when(redisson.getLock(anyString())).thenReturn(lock);
        cacheService = new RecoCacheService(redisson);
        ReflectionTestUtils.setField(cacheService, "ttlSeconds", 600);
    }

    @Test
    void getOrRefresh_cacheHit_returnsCachedWithoutLoader() throws Exception {
        RecoBaseVO base = base();
        when(bucket.get()).thenReturn(JSON.toJSONString(base));

        RecoBaseVO result = cacheService.getOrRefresh(() -> {
            throw new AssertionError("命中缓存时不应调用 loader");
        });

        assertThat(result).isEqualTo(base);
        verify(lock, never()).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void getOrRefresh_coldCache_winnerLoadsAndWrites() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        when(bucket.get()).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        RecoBaseVO result = cacheService.getOrRefresh(() -> {
            loads.incrementAndGet();
            return base();
        });

        assertThat(loads.get()).isEqualTo(1);
        assertThat(result).isNotNull();
        verify(bucket, times(1)).set(anyString(), anyLong(), any(TimeUnit.class));
        verify(lock).unlock();
    }

    @Test
    void getOrRefresh_coldCache_loserWaitsThenFallsBackToLoader() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        when(bucket.get()).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        RecoBaseVO result = cacheService.getOrRefresh(() -> {
            loads.incrementAndGet();
            return base();
        });

        assertThat(loads.get()).isEqualTo(1);
        assertThat(result).isNotNull();
        verify(bucket, never()).set(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void invalidate_deletesKey() {
        cacheService.invalidate();
        verify(bucket).delete();
    }

    @Test
    void movieChangedEvent_invalidatesBase() {
        cacheService.onMovieChanged(new MovieChangedEvent("m_1"));
        verify(bucket).delete();
    }

    /** 生产实际序列化路径：底座经 fastjson2 转字符串存 RBucket，读回再还原 */
    @Test
    void fastjson2RoundTrip_recoBaseVO() {
        RecoBaseVO base = base();
        String json = JSON.toJSONString(base);

        RecoBaseVO round = JSON.parseObject(json, RecoBaseVO.class);

        assertThat(round).isEqualTo(base);
        assertThat(round.getMovies().get("m_1").getTitle()).isEqualTo("测试片");
        assertThat(round.getSignals().get("m_1").getHotScore()).isEqualByComparingTo("88.8");
    }

    private static RecoBaseVO base() {
        RecoStats signal = new RecoStats();
        signal.setMovieId("m_1");
        signal.setWeekOrders(5);
        signal.setWeekClicks(3);
        signal.setRatingNorm(new BigDecimal("0.80"));
        signal.setFreshness(new BigDecimal("0.60"));
        signal.setHotScore(new BigDecimal("88.8"));
        signal.setComputedAt(OffsetDateTime.parse("2026-08-10T00:00:00+08:00"));

        MovieVO movie = MovieVO.builder()
                .movieId("m_1").title("测试片").posterUrl("http://x/m1.jpg")
                .genres(Collections.singletonList("科幻")).rating(new BigDecimal("8.8"))
                .durationMin(120).releaseDate("2026-08-01").status("hot_showing")
                .description("desc").cast("演员A / 演员B").wantSeeCount(10)
                .build();

        Map<String, RecoStats> signals = new HashMap<>();
        signals.put("m_1", signal);
        Map<String, MovieVO> movies = new HashMap<>();
        movies.put("m_1", movie);
        return RecoBaseVO.builder().computedAt("2026-08-10T00:00:00+08:00").signals(signals).movies(movies).build();
    }
}
