package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.constant.LockStatus;
import com.cinepass.dto.CreateLockDTO;
import com.cinepass.mapper.SeatLockMapper;
import com.cinepass.mapper.SeatMapper;
import com.cinepass.mapper.SeatStatusMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.Seat;
import com.cinepass.model.SeatLock;
import com.cinepass.model.SeatStatus;
import com.cinepass.model.ShowSchedule;
import com.cinepass.service.BookingDraftService;
import com.cinepass.service.LockService;
import com.cinepass.service.SeatInventoryService;
import com.cinepass.util.LockIds;
import com.cinepass.util.DateTimeFormats;
import com.cinepass.util.RedisUtil;
import com.cinepass.vo.LockVO;
import com.cinepass.vo.UnlockResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link LockService} 实现。
 * <p>行锁 seat_status；过期 locked 先释放再占；可选回写 Draft。
 * <p>幂等：传入 idempotencyKey 时，同 key 重复请求从 Redis 返回首次结果（TTL=锁座最大 TTL）。
 */
@Slf4j
@Service
public class LockServiceImpl implements LockService {
    private static final int DEFAULT_TTL = 900;
    private static final int MIN_TTL = 60;
    private static final int MAX_TTL = 900;
    private static final String IDEMPOTENCY_PREFIX = "idempotency:lock:";
    private static final int IDEMPOTENCY_TTL = 900;

    private final ShowMapper showMapper;
    private final SeatMapper seatMapper;
    private final SeatStatusMapper seatStatusMapper;
    private final SeatLockMapper seatLockMapper;
    private final SeatInventoryService seatInventoryService;
    private final BookingDraftService bookingDraftService;

    @Autowired(required = false)
    private RedisUtil redisUtil;

    public LockServiceImpl(ShowMapper showMapper,
                           SeatMapper seatMapper,
                           SeatStatusMapper seatStatusMapper,
                           SeatLockMapper seatLockMapper,
                           SeatInventoryService seatInventoryService,
                           BookingDraftService bookingDraftService) {
        this.showMapper = showMapper;
        this.seatMapper = seatMapper;
        this.seatStatusMapper = seatStatusMapper;
        this.seatLockMapper = seatLockMapper;
        this.seatInventoryService = seatInventoryService;
        this.bookingDraftService = bookingDraftService;
    }

    @Override
    @Transactional
    public LockVO create(String userId, CreateLockDTO dto, String idempotencyKey) {
        // 幂等检查：同 idempotencyKey 的重复请求直接返回首次结果
        if (StringUtils.hasText(idempotencyKey) && redisUtil != null) {
            String cacheKey = IDEMPOTENCY_PREFIX + idempotencyKey.trim();
            Object cached = null;
            try {
                cached = redisUtil.get(cacheKey);
            } catch (Exception e) {
                // Redis 不可用（未配置/故障/mock 未打桩）：降级跳过幂等去重，不影响锁座主流程
                log.warn("读幂等缓存失败，跳过幂等去重: {}", e.getMessage());
            }
            if (cached instanceof String) {
                LockVO cachedVo = JSON.parseObject((String) cached, LockVO.class);
                if (cachedVo != null && cachedVo.getLockId() != null) {
                    return cachedVo;
                }
            }
        }

        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED_TOKEN);
        }
        if (dto == null || !StringUtils.hasText(dto.getShowId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "showId 不能为空");
        }
        List<String> seatIds = normalizeSeatIds(dto.getSeatIds());
        if (seatIds.isEmpty() || seatIds.size() > 4) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "seatIds 长度须为 1–4");
        }

        String showId = dto.getShowId().trim();
        ShowSchedule show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        // 开场时间已过则禁止锁座，避免开演后仍可购票（系分：锁座/下单/支付统一在开场前）
        if (show.getStartTime() != null && !show.getStartTime().isAfter(DateTimeFormats.now())) {
            throw new BusinessException(ResultCode.SHOW_STARTED, "场次已开场，无法购票");
        }

        List<Seat> seats = seatMapper.selectBySeatIds(show.getSeatMapId(), seatIds);
        if (seats == null || seats.size() != seatIds.size()) {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("errorCode", "SEAT_INVALID");
            payload.put("showId", showId);
            throw new BusinessException(ResultCode.SEAT_INVALID, "seat does not belong to show", payload);
        }

        assertCoupleRule(seats, seatIds);

        seatInventoryService.ensureSeatStatus(showId, show.getSeatMapId());

        // 排序后行锁，降低死锁概率
        List<String> orderedIds = new ArrayList<String>(seatIds);
        Collections.sort(orderedIds);

        OffsetDateTime now = DateTimeFormats.now();
        seatStatusMapper.releaseExpired(showId, orderedIds, now);

        List<SeatStatus> lockedRows = seatStatusMapper.selectForUpdate(showId, orderedIds);
        List<String> conflicts = new ArrayList<String>();
        Set<String> found = new HashSet<String>();
        if (lockedRows != null) {
            for (SeatStatus ss : lockedRows) {
                found.add(ss.getSeatId());
                if (!"available".equals(ss.getStatus())) {
                    conflicts.add(ss.getSeatId());
                }
            }
        }
        for (String id : orderedIds) {
            if (!found.contains(id)) {
                conflicts.add(id);
            }
        }
        if (!conflicts.isEmpty()) {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("errorCode", "SEAT_TAKEN");
            payload.put("conflictSeatIds", conflicts);
            payload.put("showId", showId);
            throw new BusinessException(ResultCode.SEAT_TAKEN,
                    "seats already taken: " + String.join(",", conflicts), payload);
        }

        int ttl = normalizeTtl(dto.getTtlSeconds());
        OffsetDateTime expireAt = now.plusSeconds(ttl);
        String lockId = LockIds.next();
        String sessionId = StringUtils.hasText(dto.getSessionId()) ? dto.getSessionId().trim() : null;

        SeatLock lock = new SeatLock();
        lock.setLockId(lockId);
        lock.setShowId(showId);
        lock.setUserId(userId);
        lock.setSeatIdsJson(JSON.toJSONString(seatIds));
        lock.setStatus(LockStatus.ACTIVE);
        lock.setTtlSeconds(ttl);
        lock.setExpireAt(expireAt);
        lock.setSessionId(sessionId);
        lock.setCreatedAt(now);
        lock.setUpdatedAt(now);
        seatLockMapper.insert(lock);

        int updated = seatStatusMapper.markLocked(showId, orderedIds, lockId, userId, expireAt);
        if (updated != orderedIds.size()) {
            // 竞态兜底：标记行数不足视为已被占用
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("errorCode", "SEAT_TAKEN");
            payload.put("conflictSeatIds", orderedIds);
            payload.put("showId", showId);
            throw new BusinessException(ResultCode.SEAT_TAKEN, "seats already taken", payload);
        }

        if (sessionId != null) {
            bookingDraftService.bindLock(sessionId, lockId, seatIds, DateTimeFormats.format(expireAt), userId);
        }

        LockVO vo = toVo(lock, seatIds);

        // 幂等缓存：成功后写入 Redis，同 key 重复请求直接返回此结果
        if (StringUtils.hasText(idempotencyKey) && redisUtil != null) {
            try {
                redisUtil.set(
                        IDEMPOTENCY_PREFIX + idempotencyKey.trim(),
                        JSON.toJSONString(vo),
                        IDEMPOTENCY_TTL
                );
            } catch (Exception ignored) {
                // Redis 写入失败不影响主流程
            }
        }

        return vo;
    }

    @Override
    public LockVO get(String userId, String lockId) {
        SeatLock lock = requireLock(lockId);
        assertOwner(userId, lock);
        return toVo(lock, parseSeatIds(lock.getSeatIdsJson()));
    }

    @Override
    @Transactional
    public UnlockResultVO unlock(String userId, String lockId, String sessionId) {
        SeatLock lock = seatLockMapper.findByIdForUpdate(lockId);
        if (lock == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "锁座不存在");
        }
        assertOwner(userId, lock);

        // 已释放或已过期：幂等成功
        if (LockStatus.RELEASED.equals(lock.getStatus())
                || LockStatus.EXPIRED.equals(lock.getStatus())
                || LockStatus.CONSUMED.equals(lock.getStatus())) {
            if (StringUtils.hasText(sessionId)) {
                bookingDraftService.clearLockFields(sessionId.trim(), lockId, userId);
            }
            return UnlockResultVO.builder().lockId(lockId).released(Boolean.TRUE).build();
        }

        OffsetDateTime now = DateTimeFormats.now();
        if (lock.getExpireAt() != null && !lock.getExpireAt().isAfter(now)) {
            seatLockMapper.markExpired(lockId);
            seatStatusMapper.releaseByLockId(lockId);
            if (StringUtils.hasText(sessionId)) {
                bookingDraftService.clearLockFields(sessionId.trim(), lockId, userId);
            }
            return UnlockResultVO.builder().lockId(lockId).released(Boolean.TRUE).build();
        }

        seatLockMapper.markReleased(lockId);
        seatStatusMapper.releaseByLockId(lockId);
        if (StringUtils.hasText(sessionId)) {
            bookingDraftService.clearLockFields(sessionId.trim(), lockId, userId);
        }
        return UnlockResultVO.builder().lockId(lockId).released(Boolean.TRUE).build();
    }

    /** 情侣座：选中任一座则同 couple_pair_id 必须全部在批次内 */
    private void assertCoupleRule(List<Seat> seats, List<String> seatIds) {
        Set<String> selected = new HashSet<String>(seatIds);
        Set<String> pairIds = new HashSet<String>();
        for (Seat seat : seats) {
            if ("couple".equals(seat.getSeatType()) && StringUtils.hasText(seat.getCouplePairId())) {
                pairIds.add(seat.getCouplePairId());
            }
        }
        if (pairIds.isEmpty()) {
            return;
        }
        String seatMapId = seats.get(0).getSeatMapId();
        List<Seat> all = seatMapper.selectBySeatMapId(seatMapId);
        for (String pairId : pairIds) {
            for (Seat s : all) {
                if (pairId.equals(s.getCouplePairId()) && !selected.contains(s.getSeatId())) {
                    Map<String, Object> payload = new HashMap<String, Object>();
                    payload.put("errorCode", "COUPLE_RULE");
                    throw new BusinessException(ResultCode.COUPLE_RULE,
                            "couple seats must be selected together", payload);
                }
            }
        }
    }

    private List<String> normalizeSeatIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> ordered = new LinkedHashSet<String>();
        for (String id : raw) {
            if (StringUtils.hasText(id)) {
                ordered.add(id.trim());
            }
        }
        if (ordered.size() != raw.size()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "seatIds 不可重复");
        }
        return new ArrayList<String>(ordered);
    }

    private int normalizeTtl(Integer ttl) {
        if (ttl == null) {
            return DEFAULT_TTL;
        }
        if (ttl < MIN_TTL || ttl > MAX_TTL) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "ttlSeconds 须在 60–900");
        }
        return ttl;
    }

    private SeatLock requireLock(String lockId) {
        if (!StringUtils.hasText(lockId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "lockId 不能为空");
        }
        SeatLock lock = seatLockMapper.findById(lockId.trim());
        if (lock == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "锁座不存在");
        }
        return lock;
    }

    private void assertOwner(String userId, SeatLock lock) {
        if (userId == null || !userId.equals(lock.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN_PERMISSION, "无权操作该锁座");
        }
    }

    private List<String> parseSeatIds(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        List<String> parsed = JSON.parseArray(json, String.class);
        return parsed != null ? parsed : Collections.<String>emptyList();
    }

    private LockVO toVo(SeatLock lock, List<String> seatIds) {
        return LockVO.builder()
                .lockId(lock.getLockId())
                .showId(lock.getShowId())
                .seatIds(seatIds)
                .userId(lock.getUserId())
                .expireAt(lock.getExpireAt() == null ? null : DateTimeFormats.format(lock.getExpireAt()))
                .ttlSeconds(lock.getTtlSeconds())
                .status(lock.getStatus())
                .build();
    }
}
