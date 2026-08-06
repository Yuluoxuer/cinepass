package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.constant.LockStatus;
import com.cinepass.constant.OrderStatus;
import com.cinepass.dto.CancelOrderDTO;
import com.cinepass.dto.CreateOrderDTO;
import com.cinepass.mapper.OrderTicketMapper;
import com.cinepass.mapper.SeatLockMapper;
import com.cinepass.mapper.SeatStatusMapper;
import com.cinepass.mapper.ShowScheduleMapper;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.model.OrderTicket;
import com.cinepass.model.SeatLock;
import com.cinepass.model.SeatPriceRow;
import com.cinepass.model.ShowSnapshot;
import com.cinepass.model.UserAccount;
import com.cinepass.security.Roles;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.OrderService;
import com.cinepass.util.OrderIds;
import com.cinepass.vo.OrderVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.SeatPriceSnapshotVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
 * {@link OrderService} 实现。
 * <p>下单依赖有效锁座并幂等；取消仅 pending_pay；运营列表 admin 全量、staff 按 cinemaId 收窄。
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final OrderTicketMapper orderTicketMapper;
    private final SeatLockMapper seatLockMapper;
    private final SeatStatusMapper seatStatusMapper;
    private final ShowScheduleMapper showScheduleMapper;
    private final UserAccountMapper userAccountMapper;

    public OrderServiceImpl(OrderTicketMapper orderTicketMapper,
                            SeatLockMapper seatLockMapper,
                            SeatStatusMapper seatStatusMapper,
                            ShowScheduleMapper showScheduleMapper,
                            UserAccountMapper userAccountMapper) {
        this.orderTicketMapper = orderTicketMapper;
        this.seatLockMapper = seatLockMapper;
        this.seatStatusMapper = seatStatusMapper;
        this.showScheduleMapper = showScheduleMapper;
        this.userAccountMapper = userAccountMapper;
    }

    /** 由有效锁座创建订单；同 lockId 幂等返回已有单 */
    @Override
    @Transactional
    public OrderVO create(String userId, CreateOrderDTO dto) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED_TOKEN);
        }
        String lockId = dto.getLockId().trim();
        SeatLock lock = seatLockMapper.findByIdForUpdate(lockId);
        if (lock == null) {
            throw new BusinessException(ResultCode.LOCK_EXPIRED, "锁座不存在或已失效");
        }
        if (!userId.equals(lock.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN_PERMISSION, "锁座不属于当前用户");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(8));
        if (!LockStatus.ACTIVE.equals(lock.getStatus())
                || lock.getExpireAt() == null
                || !lock.getExpireAt().isAfter(now)) {
            throw new BusinessException(ResultCode.LOCK_EXPIRED);
        }

        OrderTicket existing = orderTicketMapper.findByLockId(lockId);
        if (existing != null) {
            // 同 lockId 已下过单：幂等直接返回，避免重复占座计费
            return toVo(existing);
        }

        ShowSnapshot snap = showScheduleMapper.findSnapshot(lock.getShowId());
        if (snap == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }

        List<String> seatIds = parseSeatIds(lock.getSeatIdsJson());
        if (seatIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "锁座座位为空");
        }
        List<SeatPriceRow> priceRows = showScheduleMapper.listSeatPrices(lock.getShowId(), seatIds);
        Map<String, SeatPriceRow> priceMap = new HashMap<String, SeatPriceRow>();
        if (priceRows != null) {
            for (SeatPriceRow row : priceRows) {
                priceMap.put(row.getSeatId(), row);
            }
        }
        if (priceMap.size() != seatIds.size()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "座位价区不完整");
        }

        BigDecimal amount = BigDecimal.ZERO;
        JSONArray snapshot = new JSONArray();
        for (String seatId : seatIds) {
            SeatPriceRow row = priceMap.get(seatId);
            // 价区价缺失时回退场次基础票价
            BigDecimal price = row.getPrice() != null ? row.getPrice() : snap.getBasePrice();
            amount = amount.add(price);
            JSONObject item = new JSONObject();
            item.put("seatId", seatId);
            item.put("zone", row.getZone());
            item.put("price", price);
            item.put("seatName", row.getSeatName());
            snapshot.add(item);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitPrice = amount.divide(BigDecimal.valueOf(seatIds.size()), 2, RoundingMode.HALF_UP);

        OrderTicket order = new OrderTicket();
        order.setOrderId(OrderIds.next());
        order.setUserId(userId);
        order.setShowId(lock.getShowId());
        order.setLockId(lockId);
        order.setMovieTitle(snap.getMovieTitle());
        order.setCinemaName(snap.getCinemaName());
        order.setHallName(snap.getHallName());
        order.setStartTime(snap.getStartTime());
        order.setSeatIdsJson(JSON.toJSONString(seatIds));
        order.setUnitPrice(unitPrice);
        order.setAmount(amount);
        order.setSeatPriceSnapshot(snapshot.toJSONString());
        order.setStatus(OrderStatus.PENDING_PAY);
        // 支付截止与锁座过期对齐，超时由后续支付/清扫链路处理
        order.setExpireAt(lock.getExpireAt());
        String sessionId = StringUtils.hasText(dto.getSessionId())
                ? dto.getSessionId().trim()
                : lock.getSessionId();
        order.setSessionId(sessionId);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        try {
            orderTicketMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            // 并发双插：唯一索引冲突后按 lockId 回读已有单
            OrderTicket raced = orderTicketMapper.findByLockId(lockId);
            if (raced != null) {
                return toVo(raced);
            }
            throw ex;
        }
        return toVo(order);
    }

    /** 本人订单详情；非本人拒绝 */
    @Override
    public OrderVO getMine(String userId, String orderId) {
        OrderTicket order = requireOrder(orderId);
        assertOwner(userId, order);
        return toVo(order);
    }

    /** 本人订单分页；可选 status 过滤 */
    @Override
    public PageResult<OrderVO> listMine(String userId, String status, int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        status = normalizeStatus(status);
        long total = orderTicketMapper.countByUser(userId, status);
        if (total == 0) {
            return new PageResult<OrderVO>(Collections.<OrderVO>emptyList(), page, size, 0);
        }
        List<OrderTicket> rows = orderTicketMapper.listByUser(userId, status, (page - 1) * size, size);
        return new PageResult<OrderVO>(toVoList(rows), page, size, total);
    }

    /** 取消待支付订单并释放锁座；已取消幂等返回 */
    @Override
    @Transactional
    public OrderVO cancel(String userId, String orderId, CancelOrderDTO dto) {
        OrderTicket order = orderTicketMapper.findByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        assertOwner(userId, order);
        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            // 已取消：幂等返回当前态
            return toVo(order);
        }
        if (!OrderStatus.PENDING_PAY.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_NOT_CANCELLABLE);
        }
        String reason = dto != null && StringUtils.hasText(dto.getReason())
                ? dto.getReason().trim()
                : "user_cancel";
        int n = orderTicketMapper.updateCancelled(orderId, reason);
        if (n == 0) {
            throw new BusinessException(ResultCode.ORDER_NOT_CANCELLABLE);
        }
        // 释放锁座凭证与座位库存，避免座位长期占用
        seatLockMapper.markReleased(order.getLockId());
        seatStatusMapper.releaseByLockId(order.getLockId());

        OrderTicket refreshed = orderTicketMapper.findById(orderId);
        return toVo(refreshed);
    }

    /** 运营协助查单：admin 全量；staff 仅本影院场次订单 */
    @Override
    public PageResult<OrderVO> listAdmin(String filterUserId, String status,
                                         LocalDate dateFrom, LocalDate dateTo,
                                         int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        status = normalizeStatus(status);

        String cinemaId = null;
        if (!SecurityContext.isAdmin()) {
            if (!Roles.STAFF.equals(SecurityContext.getCurrentRole())) {
                throw new BusinessException(ResultCode.FORBIDDEN_PERMISSION);
            }
            // staff 仅能看本影院场次订单；未绑 cinemaId 直接拒绝
            cinemaId = SecurityContext.getCurrentCinemaId();
            if (!StringUtils.hasText(cinemaId)) {
                throw new BusinessException(ResultCode.FORBIDDEN_PERMISSION, "staff 未绑定影院");
            }
        }

        long total = orderTicketMapper.countAdmin(filterUserId, status, dateFrom, dateTo, cinemaId);
        if (total == 0) {
            return new PageResult<OrderVO>(Collections.<OrderVO>emptyList(), page, size, 0);
        }
        List<OrderTicket> rows = orderTicketMapper.listAdmin(
                filterUserId, status, dateFrom, dateTo, cinemaId, (page - 1) * size, size);
        return new PageResult<OrderVO>(toVoList(rows), page, size, total);
    }

    /** 按 ID 取订单，不存在则 404 */
    private OrderTicket requireOrder(String orderId) {
        OrderTicket order = orderTicketMapper.findById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    /** 校验订单归属当前用户 */
    private void assertOwner(String userId, OrderTicket order) {
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN_PERMISSION, "无权查看该订单");
        }
    }

    /** 解析座位 ID JSON；去空白、去重并保序 */
    private List<String> parseSeatIds(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        List<String> parsed = JSON.parseArray(json, String.class);
        if (parsed == null || parsed.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> ordered = new LinkedHashSet<String>();
        for (String id : parsed) {
            if (StringUtils.hasText(id)) {
                ordered.add(id.trim());
            }
        }
        return new ArrayList<String>(ordered);
    }

    /** 批量转为 OrderVO；批量查询用户昵称避免 N+1 */
    private List<OrderVO> toVoList(List<OrderTicket> rows) {
        List<OrderVO> items = new ArrayList<OrderVO>();
        if (rows == null || rows.isEmpty()) {
            return items;
        }
        // 收集所有 userId，批量查询昵称
        Set<String> userIds = new LinkedHashSet<String>();
        for (OrderTicket row : rows) {
            if (StringUtils.hasText(row.getUserId())) {
                userIds.add(row.getUserId());
            }
        }
        Map<String, String> nicknameMap = new HashMap<String, String>();
        if (!userIds.isEmpty()) {
            List<UserAccount> users = userAccountMapper.findByIds(new ArrayList<String>(userIds));
            if (users != null) {
                for (UserAccount u : users) {
                    nicknameMap.put(u.getUserId(), u.getNickname());
                }
            }
        }
        for (OrderTicket row : rows) {
            items.add(toVo(row, nicknameMap.get(row.getUserId())));
        }
        return items;
    }

    /** OrderTicket → OrderVO；时间格式化为 ISO-8601 */
    private OrderVO toVo(OrderTicket o) {
        return toVo(o, null);
    }

    /** OrderTicket → OrderVO；可传入昵称 */
    private OrderVO toVo(OrderTicket o, String nickname) {
        return OrderVO.builder()
                .orderId(o.getOrderId())
                .userId(o.getUserId())
                .nickname(nickname)
                .showId(o.getShowId())
                .movieTitle(o.getMovieTitle())
                .cinemaName(o.getCinemaName())
                .hallName(o.getHallName())
                .startTime(format(o.getStartTime()))
                .seatIds(parseSeatIds(o.getSeatIdsJson()))
                .seatPrices(parseSeatPrices(o.getSeatPriceSnapshot()))
                .unitPrice(o.getUnitPrice())
                .amount(o.getAmount())
                .status(o.getStatus())
                .ticketCode(o.getTicketCode())
                .qrPayload(o.getQrPayload())
                .lockId(o.getLockId())
                .expireAt(format(o.getExpireAt()))
                .createdAt(format(o.getCreatedAt()))
                .payAt(format(o.getPayAt()))
                .payChannel(o.getPayChannel())
                .build();
    }

    /** seat_price_snapshot JSON → SeatPriceSnapshotVO 列表 */
    private List<SeatPriceSnapshotVO> parseSeatPrices(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            JSONArray arr = JSON.parseArray(json);
            if (arr == null || arr.isEmpty()) {
                return Collections.emptyList();
            }
            List<SeatPriceSnapshotVO> items = new ArrayList<SeatPriceSnapshotVO>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj == null) {
                    continue;
                }
                items.add(SeatPriceSnapshotVO.builder()
                        .seatId(obj.getString("seatId"))
                        .zone(obj.getString("zone"))
                        .price(obj.getBigDecimal("price"))
                        .seatName(obj.getString("seatName"))
                        .build());
            }
            return items;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** OffsetDateTime → ISO-8601 字符串；null 保持 null */
    private String format(OffsetDateTime t) {
        return t == null ? null : ISO.format(t);
    }

    /** page&lt;1 时回落为 1 */
    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    /** size&lt;1 默认 20；上限 50 */
    private int normalizeSize(int size) {
        // 列表接口硬上限 50，防止一次拉过大页
        if (size < 1) {
            return 20;
        }
        return size > 50 ? 50 : size;
    }

    /** 校验并规范化订单状态；空串视为不筛选，非法值抛 PARAM_ERROR */
    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String s = status.trim();
        if (OrderStatus.PENDING_PAY.equals(s)
                || OrderStatus.ISSUED.equals(s)
                || OrderStatus.CANCELLED.equals(s)) {
            return s;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "非法订单状态");
    }
}
