package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.constant.CacheKeys;
import com.cinepass.mapper.AuthRefreshSessionMapper;
import com.cinepass.model.AuthRefreshSession;
import com.cinepass.service.AuthSessionService.RefreshSession;
import com.cinepass.util.DateTimeFormats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthSessionServiceImpl：DB 真相 + Redis 缓存 / deny fail-open。
 */
@ExtendWith(MockitoExtension.class)
class AuthSessionServiceImplTest {

    private static final long TTL = 604800L;
    private static final String SID = "sid_test_1";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private AuthRefreshSessionMapper sessionMapper;

    private AuthSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionServiceImpl(redis, sessionMapper, TTL);
    }

    @Test
    void saveRefresh_writesDbThenRedis() {
        when(redis.opsForValue()).thenReturn(valueOps);

        service.saveRefresh(SID, "u1", "user");

        ArgumentCaptor<AuthRefreshSession> cap = ArgumentCaptor.forClass(AuthRefreshSession.class);
        verify(sessionMapper).upsert(cap.capture());
        AuthRefreshSession row = cap.getValue();
        assertThat(row.getSid()).isEqualTo(SID);
        assertThat(row.getUserId()).isEqualTo("u1");
        assertThat(row.getRole()).isEqualTo("user");
        assertThat(row.getRefreshJti()).isNotBlank();
        assertThat(row.getExpireAt()).isAfter(DateTimeFormats.now());

        verify(valueOps).set(eq(CacheKeys.refreshTokenKey(SID)), anyString(), eq(TTL), eq(TimeUnit.SECONDS));
    }

    @Test
    void saveRefresh_redisFail_stillSucceedsAfterDb() {
        when(redis.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("redis down")).when(valueOps)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        service.saveRefresh(SID, "u1", "user");

        verify(sessionMapper).upsert(any(AuthRefreshSession.class));
    }

    @Test
    void getRefresh_redisHit_skipsDb() {
        when(redis.opsForValue()).thenReturn(valueOps);
        RefreshSession cached = new RefreshSession();
        cached.setUserId("u1");
        cached.setRole("user");
        cached.setRefreshJti("rj1");
        when(valueOps.get(CacheKeys.refreshTokenKey(SID))).thenReturn(JSON.toJSONString(cached));

        Optional<RefreshSession> got = service.getRefresh(SID);

        assertThat(got).isPresent();
        assertThat(got.get().getUserId()).isEqualTo("u1");
        verify(sessionMapper, never()).findValidBySid(anyString(), any(OffsetDateTime.class));
    }

    @Test
    void getRefresh_redisMiss_fallsBackToDbAndBackfills() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CacheKeys.refreshTokenKey(SID))).thenReturn(null);

        AuthRefreshSession row = new AuthRefreshSession();
        row.setSid(SID);
        row.setUserId("u2");
        row.setRole("admin");
        row.setRefreshJti("rj2");
        row.setExpireAt(DateTimeFormats.now().plusDays(1));
        when(sessionMapper.findValidBySid(eq(SID), any(OffsetDateTime.class))).thenReturn(row);

        Optional<RefreshSession> got = service.getRefresh(SID);

        assertThat(got).isPresent();
        assertThat(got.get().getUserId()).isEqualTo("u2");
        assertThat(got.get().getRole()).isEqualTo("admin");
        verify(valueOps).set(eq(CacheKeys.refreshTokenKey(SID)), anyString(), eq(TTL), eq(TimeUnit.SECONDS));
    }

    @Test
    void getRefresh_redisError_fallsBackToDb() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CacheKeys.refreshTokenKey(SID))).thenThrow(new RuntimeException("redis down"));

        AuthRefreshSession row = new AuthRefreshSession();
        row.setSid(SID);
        row.setUserId("u3");
        row.setRole("staff");
        row.setRefreshJti("rj3");
        when(sessionMapper.findValidBySid(eq(SID), any(OffsetDateTime.class))).thenReturn(row);

        Optional<RefreshSession> got = service.getRefresh(SID);

        assertThat(got).isPresent();
        assertThat(got.get().getUserId()).isEqualTo("u3");
    }

    @Test
    void deleteRefresh_deletesDbAndRedis() {
        service.deleteRefresh(SID);
        verify(sessionMapper).deleteBySid(SID);
        verify(redis).delete(CacheKeys.refreshTokenKey(SID));
    }

    @Test
    void isDenied_redisError_failOpen() {
        when(redis.hasKey(CacheKeys.denyJtiKey("jti1"))).thenThrow(new RuntimeException("redis down"));
        assertThat(service.isDenied("jti1")).isFalse();
    }

    @Test
    void isDenied_hit_returnsTrue() {
        when(redis.hasKey(CacheKeys.denyJtiKey("jti2"))).thenReturn(true);
        assertThat(service.isDenied("jti2")).isTrue();
    }

    @Test
    void denyJti_redisError_doesNotThrow() {
        when(redis.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("redis down")).when(valueOps)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        service.denyJti("jti3", 60);
    }
}
