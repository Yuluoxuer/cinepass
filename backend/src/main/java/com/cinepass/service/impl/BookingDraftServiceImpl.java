package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.constant.BookingStates;
import com.cinepass.dto.CreateBookingDraftDTO;
import com.cinepass.dto.UpdateBookingDraftDTO;
import com.cinepass.mapper.AgentSessionMapper;
import com.cinepass.model.AgentSession;
import com.cinepass.service.BookingDraftService;
import com.cinepass.util.SessionIds;
import com.cinepass.util.DateTimeFormats;
import com.cinepass.vo.BookingDraftVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BookingDraft 实现：落库 agent_session，CAS version，依赖清空与 firstIncompleteStep。
 */
@Service
public class BookingDraftServiceImpl implements BookingDraftService {

    private static final Set<String> PATCH_ALLOWED;
    static {
        Set<String> s = new LinkedHashSet<String>();
        s.add("source");
        s.add("state");
        s.add("intent");
        s.add("movieId");
        s.add("filmTitle");
        s.add("genre");
        s.add("date");
        s.add("timeWindow");
        s.add("lat");
        s.add("lng");
        s.add("cinemaId");
        s.add("showId");
        s.add("count");
        s.add("seatIds");
        s.add("preferRow");
        s.add("preferSide");
        s.add("together");
        s.add("budgetMax");
        s.add("listContext");
        PATCH_ALLOWED = Collections.unmodifiableSet(s);
    }

    private final AgentSessionMapper agentSessionMapper;

    public BookingDraftServiceImpl(AgentSessionMapper agentSessionMapper) {
        this.agentSessionMapper = agentSessionMapper;
    }

    @Override
    @Transactional
    public BookingDraftVO create(CreateBookingDraftDTO dto, String currentUserId) {
        String source = "manual";
        if (dto != null && StringUtils.hasText(dto.getSource())) {
            source = dto.getSource().trim();
        }
        String movieId = dto != null && StringUtils.hasText(dto.getMovieId())
                ? dto.getMovieId().trim() : null;

        OffsetDateTime now = DateTimeFormats.now();
        String sessionId = SessionIds.next();

        BookingDraftVO draft = emptyDraft(sessionId, source, currentUserId, now);
        if (movieId != null) {
            draft.setMovieId(movieId);
            draft.setState(BookingStates.SELECT_CINEMA);
        }
        persistNew(draft, now);
        return draft;
    }

    @Override
    @Transactional
    public BookingDraftVO get(String sessionId, String currentUserId) {
        requireSessionId(sessionId);
        AgentSession row = agentSessionMapper.findById(sessionId);
        if (row == null) {
            OffsetDateTime now = DateTimeFormats.now();
            BookingDraftVO draft = emptyDraft(sessionId, "manual", currentUserId, now);
            persistNew(draft, now);
            return draft;
        }
        BookingDraftVO draft = fromRow(row);
        assertCanRead(draft, currentUserId);
        if (!StringUtils.hasText(draft.getUserId()) && StringUtils.hasText(currentUserId)) {
            draft.setUserId(currentUserId);
            // 懒绑定登录用户，不升 version（仅补列）
            row.setUserId(currentUserId);
            row.setDraftJson(JSON.toJSONString(draft));
            // 用 CAS 同 version 写回会失败；直接 bump 不合适。简单：仅内存返回绑定，下次 update 写入。
        }
        return draft;
    }

    @Override
    @Transactional
    public void bindLock(String sessionId, String lockId, List<String> seatIds,
                         String expireAt, String userId) {
        requireSessionId(sessionId);
        AgentSession row = agentSessionMapper.findByIdForUpdate(sessionId);
        if (row == null) {
            get(sessionId, userId);
            row = agentSessionMapper.findByIdForUpdate(sessionId);
        }
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Draft 不存在");
        }
        BookingDraftVO draft = fromRow(row);
        assertCanWrite(draft, userId);
        if (StringUtils.hasText(userId)) {
            draft.setUserId(userId);
        }
        draft.setLockId(lockId);
        draft.setSeatIds(seatIds != null ? new ArrayList<String>(seatIds) : new ArrayList<String>());
        draft.setExpireAt(expireAt);
        draft.setState(BookingStates.CONFIRM_ORDER);
        draft.setVersion(draft.getVersion() == null ? 1L : draft.getVersion() + 1);
        OffsetDateTime now = DateTimeFormats.now();
        draft.setUpdatedAt(DateTimeFormats.format(now));
        AgentSession upd = toRow(draft, row.getCreatedAt(), now);
        agentSessionMapper.updateCas(upd);
    }

    @Override
    @Transactional
    public void clearLockFields(String sessionId, String lockId, String userId) {
        requireSessionId(sessionId);
        AgentSession row = agentSessionMapper.findByIdForUpdate(sessionId);
        if (row == null) {
            return;
        }
        BookingDraftVO draft = fromRow(row);
        // 仅清与本 lockId 绑定的 Draft，避免误清他人会话
        if (!StringUtils.hasText(lockId) || !lockId.equals(draft.getLockId())) {
            return;
        }
        assertCanWrite(draft, userId);
        draft.setLockId(null);
        draft.setSeatIds(new ArrayList<String>());
        draft.setExpireAt(null);
        if (!StringUtils.hasText(draft.getOrderId())) {
            draft.setState(BookingStates.SELECT_SEAT);
        }
        draft.setVersion(draft.getVersion() == null ? 1L : draft.getVersion() + 1);
        OffsetDateTime now = DateTimeFormats.now();
        draft.setUpdatedAt(DateTimeFormats.format(now));
        AgentSession upd = toRow(draft, row.getCreatedAt(), now);
        agentSessionMapper.updateCas(upd);
    }

    @Override
    @Transactional
    public BookingDraftVO update(String sessionId, UpdateBookingDraftDTO dto, String currentUserId) {
        requireSessionId(sessionId);
        if (dto == null || dto.getVersion() == null || dto.getPatch() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "version/patch 必填");
        }
        AgentSession row = agentSessionMapper.findByIdForUpdate(sessionId);
        if (row == null) {
            // 懒创建后再更新
            get(sessionId, currentUserId);
            row = agentSessionMapper.findByIdForUpdate(sessionId);
        }
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Draft 不存在");
        }
        BookingDraftVO current = fromRow(row);
        assertCanWrite(current, currentUserId);

        if (!dto.getVersion().equals(current.getVersion())) {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("errorCode", "DRAFT_CONFLICT");
            payload.put("serverDraft", current);
            throw new BusinessException(ResultCode.DRAFT_CONFLICT, "draft version mismatch", payload);
        }

        BookingDraftVO next = applyPatch(current, dto.getPatch(), currentUserId);
        next.setVersion(current.getVersion() + 1);
        OffsetDateTime now = DateTimeFormats.now();
        next.setUpdatedAt(DateTimeFormats.format(now));

        AgentSession upd = toRow(next, row.getCreatedAt(), now);
        int n = agentSessionMapper.updateCas(upd);
        if (n == 0) {
            BookingDraftVO latest = fromRow(agentSessionMapper.findById(sessionId));
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("errorCode", "DRAFT_CONFLICT");
            payload.put("serverDraft", latest);
            throw new BusinessException(ResultCode.DRAFT_CONFLICT, "draft version mismatch", payload);
        }
        return next;
    }

    @Override
    @Transactional
    public BookingDraftVO merge(String sessionId, Map<String, Object> incoming, String currentUserId) {
        requireSessionId(sessionId);
        if (incoming == null || incoming.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "draft 不能为空");
        }
        AgentSession row = agentSessionMapper.findByIdForUpdate(sessionId);
        if (row == null) {
            get(sessionId, currentUserId);
            row = agentSessionMapper.findByIdForUpdate(sessionId);
        }
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Draft 不存在");
        }
        BookingDraftVO current = fromRow(row);
        assertCanWrite(current, currentUserId);

        long serverVersion = current.getVersion() == null ? 0L : current.getVersion();
        Long incomingVersion = asLong(incoming.get("version"));
        long inVer = incomingVersion == null ? serverVersion : incomingVersion;
        boolean serverNewer = inVer < serverVersion;

        BookingDraftVO next = copy(current);

        // ① 决策字段：incoming 优先；服务端较新且已有值时不覆盖（防 Agent 旧快照回退页面新选择）
        for (String key : PATCH_ALLOWED) {
            if (!incoming.containsKey(key) || incoming.get(key) == null) {
                continue;
            }
            if (serverNewer && hasServerValue(next, key)) {
                continue;
            }
            applyField(next, key, incoming.get(key));
        }

        // ② 级联清理：改影片/影院/场次/座位 → 清空依赖与锁/单
        // 用「合并后实际结果 next」与「服务端基线 current」比较，避免 serverNewer 跳过 incoming
        // 时，被 Agent 旧快照误判为决策变更而级联清空服务端的新选择。
        boolean movieChanged = !eq(current.getMovieId(), next.getMovieId());
        boolean cinemaChanged = !eq(current.getCinemaId(), next.getCinemaId());
        boolean showChanged = !eq(current.getShowId(), next.getShowId());
        boolean seatChanged = !listEq(current.getSeatIds(), next.getSeatIds());

        if (movieChanged) {
            next.setCinemaId(null);
            next.setShowId(null);
            next.setSeatIds(new ArrayList<String>());
            next.setLockId(null);
            next.setOrderId(null);
            next.setExpireAt(null);
        } else if (cinemaChanged) {
            next.setShowId(null);
            next.setSeatIds(new ArrayList<String>());
            next.setLockId(null);
            next.setOrderId(null);
            next.setExpireAt(null);
        } else if (showChanged) {
            next.setSeatIds(new ArrayList<String>());
            next.setLockId(null);
            next.setOrderId(null);
            next.setExpireAt(null);
        }

        boolean progressInvalidated = movieChanged || cinemaChanged || showChanged || seatChanged;

        // ③ 锁座/下单成果保护：决策未变时服务端非空优先、其次 incoming；决策已变则仅保留 incoming 明确成果
        if (!progressInvalidated) {
            if (StringUtils.hasText(current.getLockId())) {
                next.setLockId(current.getLockId());
            } else {
                next.setLockId(emptyToNull(asString(incoming.get("lockId"))));
            }
            if (StringUtils.hasText(current.getOrderId())) {
                next.setOrderId(current.getOrderId());
            } else {
                next.setOrderId(emptyToNull(asString(incoming.get("orderId"))));
            }
            if (StringUtils.hasText(current.getExpireAt())) {
                next.setExpireAt(current.getExpireAt());
            } else {
                next.setExpireAt(emptyToNull(asString(incoming.get("expireAt"))));
            }
        } else {
            next.setLockId(emptyToNull(asString(incoming.get("lockId"))));
            next.setOrderId(emptyToNull(asString(incoming.get("orderId"))));
            next.setExpireAt(emptyToNull(asString(incoming.get("expireAt"))));
        }

        // ④ source/state 收敛
        if ("agent".equals(incoming.get("source"))) {
            next.setSource("agent");
        } else if (!StringUtils.hasText(next.getSource())) {
            next.setSource("agent");
        }
        if (next.getCount() == null || next.getCount() < 1) {
            next.setCount(1);
        }
        if (next.getCount() > 4) {
            next.setCount(4);
        }
        if (next.getSeatIds() == null) {
            next.setSeatIds(new ArrayList<String>());
        }
        next.setState(resolveState(next, null));

        // ⑤ version = max + 1
        next.setVersion(Math.max(serverVersion, inVer) + 1);
        OffsetDateTime now = DateTimeFormats.now();
        next.setUpdatedAt(DateTimeFormats.format(now));

        AgentSession upd = toRow(next, row.getCreatedAt(), now);
        int n = agentSessionMapper.updateCas(upd);
        if (n == 0) {
            // 并发写竞争：重读最新返回，调用方以返回值为准
            return fromRow(agentSessionMapper.findById(sessionId));
        }
        return next;
    }

    private BookingDraftVO applyPatch(BookingDraftVO base, Map<String, Object> patch,
                                      String currentUserId) {
        BookingDraftVO next = copy(base);
        if (StringUtils.hasText(currentUserId)) {
            next.setUserId(currentUserId);
        }

        String oldMovie = base.getMovieId();
        String oldCinema = base.getCinemaId();
        String oldShow = base.getShowId();

        for (Map.Entry<String, Object> e : patch.entrySet()) {
            String key = e.getKey();
            if (!PATCH_ALLOWED.contains(key)) {
                // 忽略禁止字段（lockId/orderId/expireAt/userId/sessionId/version…）
                continue;
            }
            applyField(next, key, e.getValue());
        }

        // 依赖清空
        boolean movieChanged = hasKey(patch, "movieId")
                && !eq(oldMovie, next.getMovieId());
        boolean cinemaChanged = hasKey(patch, "cinemaId")
                && !eq(oldCinema, next.getCinemaId());
        boolean showChanged = hasKey(patch, "showId")
                && !eq(oldShow, next.getShowId());

        if (movieChanged) {
            if (!hasKey(patch, "cinemaId")) {
                next.setCinemaId(null);
            }
            next.setShowId(null);
            next.setSeatIds(new ArrayList<String>());
            next.setLockId(null);
            next.setOrderId(null);
            next.setExpireAt(null);
        } else if (cinemaChanged) {
            next.setShowId(null);
            next.setSeatIds(new ArrayList<String>());
            next.setLockId(null);
            next.setOrderId(null);
            next.setExpireAt(null);
        } else if (showChanged) {
            next.setSeatIds(new ArrayList<String>());
            next.setLockId(null);
            next.setOrderId(null);
            next.setExpireAt(null);
        }

        // source：客户端从 agent 手改时变 hybrid（若原 agent 且改了选座相关）
        if ("agent".equals(base.getSource())
                && (hasKey(patch, "seatIds") || hasKey(patch, "showId") || hasKey(patch, "cinemaId")
                || hasKey(patch, "movieId"))
                && !hasKey(patch, "source")) {
            next.setSource("hybrid");
        }

        next.setState(resolveState(next, hasKey(patch, "state") ? next.getState() : null));
        if (next.getCount() == null || next.getCount() < 1) {
            next.setCount(1);
        }
        if (next.getCount() > 4) {
            next.setCount(4);
        }
        if (next.getSeatIds() == null) {
            next.setSeatIds(new ArrayList<String>());
        }
        return next;
    }

    private void applyField(BookingDraftVO d, String key, Object value) {
        if ("source".equals(key)) {
            String v = asString(value);
            if ("manual".equals(v) || "agent".equals(v) || "hybrid".equals(v)) {
                d.setSource(v);
            }
        } else if ("state".equals(key)) {
            d.setState(asString(value));
        } else if ("intent".equals(key)) {
            d.setIntent(asString(value));
        } else if ("movieId".equals(key)) {
            d.setMovieId(emptyToNull(asString(value)));
        } else if ("filmTitle".equals(key)) {
            d.setFilmTitle(asString(value));
        } else if ("genre".equals(key)) {
            d.setGenre(asString(value));
        } else if ("date".equals(key)) {
            d.setDate(asString(value));
        } else if ("timeWindow".equals(key)) {
            d.setTimeWindow(asString(value));
        } else if ("lat".equals(key)) {
            d.setLat(asDecimal(value));
        } else if ("lng".equals(key)) {
            d.setLng(asDecimal(value));
        } else if ("cinemaId".equals(key)) {
            d.setCinemaId(emptyToNull(asString(value)));
        } else if ("showId".equals(key)) {
            d.setShowId(emptyToNull(asString(value)));
        } else if ("count".equals(key)) {
            Integer c = asInt(value);
            if (c != null) {
                d.setCount(c);
            }
        } else if ("seatIds".equals(key)) {
            d.setSeatIds(asStringList(value));
        } else if ("preferRow".equals(key)) {
            d.setPreferRow(asString(value));
        } else if ("preferSide".equals(key)) {
            d.setPreferSide(asString(value));
        } else if ("together".equals(key)) {
            d.setTogether(asBool(value));
        } else if ("budgetMax".equals(key)) {
            d.setBudgetMax(asDecimal(value));
        } else if ("listContext".equals(key)) {
            if (value == null) {
                d.setListContext(null);
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) value;
                d.setListContext(m);
            } else {
                d.setListContext(JSON.parseObject(JSON.toJSONString(value),
                        new TypeReference<Map<String, Object>>() { }));
            }
        }
    }

    /**
     * firstIncompleteStep；若客户端显式写了 PayMock/TicketIssued 且字段支持则保留。
     */
    private String resolveState(BookingDraftVO d, String explicitState) {
        if (BookingStates.TICKET_ISSUED.equals(explicitState) && StringUtils.hasText(d.getOrderId())) {
            return BookingStates.TICKET_ISSUED;
        }
        if (BookingStates.PAY_MOCK.equals(explicitState) && StringUtils.hasText(d.getLockId())) {
            return BookingStates.PAY_MOCK;
        }
        if (!StringUtils.hasText(d.getMovieId())) {
            return BookingStates.SELECT_MOVIE;
        }
        if (!StringUtils.hasText(d.getCinemaId())) {
            return BookingStates.SELECT_CINEMA;
        }
        if (!StringUtils.hasText(d.getShowId())) {
            return BookingStates.SELECT_SHOW;
        }
        if (!StringUtils.hasText(d.getLockId())) {
            return BookingStates.SELECT_SEAT;
        }
        if (!StringUtils.hasText(d.getOrderId())) {
            if (BookingStates.PAY_MOCK.equals(d.getState()) || BookingStates.PAY_MOCK.equals(explicitState)) {
                return BookingStates.PAY_MOCK;
            }
            return BookingStates.CONFIRM_ORDER;
        }
        if (BookingStates.TICKET_ISSUED.equals(d.getState()) || BookingStates.TICKET_ISSUED.equals(explicitState)) {
            return BookingStates.TICKET_ISSUED;
        }
        return BookingStates.PAY_MOCK;
    }

    private void persistNew(BookingDraftVO draft, OffsetDateTime now) {
        AgentSession row = toRow(draft, now, now);
        agentSessionMapper.insert(row);
    }

    private AgentSession toRow(BookingDraftVO draft, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        AgentSession row = new AgentSession();
        row.setSessionId(draft.getSessionId());
        row.setUserId(draft.getUserId());
        row.setSource(draft.getSource());
        row.setState(draft.getState());
        row.setVersion(draft.getVersion());
        row.setDraftJson(JSON.toJSONString(draft));
        row.setCreatedAt(createdAt);
        row.setUpdatedAt(updatedAt);
        return row;
    }

    private BookingDraftVO fromRow(AgentSession row) {
        BookingDraftVO draft;
        if (StringUtils.hasText(row.getDraftJson())) {
            draft = JSON.parseObject(row.getDraftJson(), BookingDraftVO.class);
        } else {
            draft = new BookingDraftVO();
        }
        if (draft == null) {
            draft = new BookingDraftVO();
        }
        draft.setSessionId(row.getSessionId());
        draft.setUserId(row.getUserId());
        draft.setSource(row.getSource());
        draft.setState(row.getState());
        draft.setVersion(row.getVersion());
        if (row.getUpdatedAt() != null) {
            draft.setUpdatedAt(DateTimeFormats.format(row.getUpdatedAt()));
        }
        if (draft.getCount() == null) {
            draft.setCount(1);
        }
        if (draft.getSeatIds() == null) {
            draft.setSeatIds(new ArrayList<String>());
        }
        return draft;
    }

    private BookingDraftVO emptyDraft(String sessionId, String source, String userId, OffsetDateTime now) {
        return BookingDraftVO.builder()
                .sessionId(sessionId)
                .userId(userId)
                .source(source)
                .state(BookingStates.IDLE)
                .count(1)
                .seatIds(new ArrayList<String>())
                .version(0L)
                .updatedAt(DateTimeFormats.format(now))
                .build();
    }

    private BookingDraftVO copy(BookingDraftVO src) {
        return JSON.parseObject(JSON.toJSONString(src), BookingDraftVO.class);
    }

    private void assertCanRead(BookingDraftVO draft, String currentUserId) {
        boolean sensitive = StringUtils.hasText(draft.getLockId()) || StringUtils.hasText(draft.getOrderId());
        if (!sensitive) {
            return;
        }
        if (!StringUtils.hasText(draft.getUserId())) {
            return;
        }
        if (!draft.getUserId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN_PERMISSION, "无权查看该 Draft");
        }
    }

    private void assertCanWrite(BookingDraftVO draft, String currentUserId) {
        if (StringUtils.hasText(draft.getUserId())) {
            // draft 已绑定用户但当前请求无 token → 引导登录（401）
            if (!StringUtils.hasText(currentUserId)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED_TOKEN, "请先登录后再修改购票草稿");
            }
            // draft 已绑定用户且当前用户不是本人 → 无权（403）
            if (!draft.getUserId().equals(currentUserId)) {
                throw new BusinessException(ResultCode.FORBIDDEN_PERMISSION, "无权修改该 Draft");
            }
        }
        // 已有锁座字段时匿名禁止写
        if ((StringUtils.hasText(draft.getLockId()) || StringUtils.hasText(draft.getOrderId()))
                && !StringUtils.hasText(currentUserId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED_TOKEN, "请先登录");
        }
    }

    private void requireSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "sessionId 不能为空");
        }
    }

    private static boolean hasKey(Map<String, Object> patch, String key) {
        return patch != null && patch.containsKey(key);
    }

    private static boolean eq(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String emptyToNull(String v) {
        return StringUtils.hasText(v) ? v : null;
    }

    private static Integer asInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean listEq(List<String> a, List<String> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        return new LinkedHashSet<String>(a).equals(new LinkedHashSet<String>(b));
    }

    /** 服务端草稿某决策字段是否已有非空值（用于 serverNewer 时不覆盖）。 */
    private boolean hasServerValue(BookingDraftVO d, String key) {
        Object v = null;
        if ("source".equals(key)) v = d.getSource();
        else if ("state".equals(key)) v = d.getState();
        else if ("intent".equals(key)) v = d.getIntent();
        else if ("movieId".equals(key)) v = d.getMovieId();
        else if ("filmTitle".equals(key)) v = d.getFilmTitle();
        else if ("genre".equals(key)) v = d.getGenre();
        else if ("date".equals(key)) v = d.getDate();
        else if ("timeWindow".equals(key)) v = d.getTimeWindow();
        else if ("lat".equals(key)) v = d.getLat();
        else if ("lng".equals(key)) v = d.getLng();
        else if ("cinemaId".equals(key)) v = d.getCinemaId();
        else if ("showId".equals(key)) v = d.getShowId();
        else if ("count".equals(key)) v = d.getCount();
        else if ("seatIds".equals(key)) v = d.getSeatIds();
        else if ("preferRow".equals(key)) v = d.getPreferRow();
        else if ("preferSide".equals(key)) v = d.getPreferSide();
        else if ("together".equals(key)) v = d.getTogether();
        else if ("budgetMax".equals(key)) v = d.getBudgetMax();
        else if ("listContext".equals(key)) v = d.getListContext();
        if (v instanceof List) {
            return !((List<?>) v).isEmpty();
        }
        if (v instanceof Map) {
            return !((Map<?, ?>) v).isEmpty();
        }
        return StringUtils.hasText(asString(v));
    }

    private static BigDecimal asDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return BigDecimal.valueOf(((Number) v).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean asBool(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object v) {
        if (v == null) {
            return new ArrayList<String>();
        }
        if (v instanceof List) {
            List<String> out = new ArrayList<String>();
            for (Object o : (List<?>) v) {
                if (o != null && StringUtils.hasText(String.valueOf(o))) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        return JSON.parseArray(JSON.toJSONString(v), String.class);
    }
}
