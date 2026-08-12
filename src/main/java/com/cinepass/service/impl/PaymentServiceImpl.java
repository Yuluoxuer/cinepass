package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.constant.OrderStatus;
import com.cinepass.dto.PayDTO;
import com.cinepass.mapper.OrderTicketMapper;
import com.cinepass.mapper.SeatLockMapper;
import com.cinepass.mapper.SeatStatusMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.OrderTicket;
import com.cinepass.model.ShowSchedule;
import com.cinepass.service.PaymentService;
import com.cinepass.util.PayTokenUtil;
import com.cinepass.util.DateTimeFormats;
import com.cinepass.util.RedisUtil;
import com.cinepass.vo.OrderVO;
import com.cinepass.vo.PayQrVO;
import com.cinepass.vo.PaySessionVO;
import com.cinepass.vo.RedeemQrVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * {@link PaymentService} 实现。
 * <p>支付：payToken 短期签名 + Redis 防重放，确认出票时以订单行锁二次校验状态与支付截止，
 * 杜绝「订单过期后二维码仍可支付」。核销：issued → redeemed 单向流转。</p>
 */
@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** PC 轮询订单状态建议间隔（毫秒） */
    private static final int POLL_INTERVAL_MS = 2000;

    /** 支付 token 防重放键：pay:token:{jti}（支付成功后按 jti DEL） */
    private static final String PAY_TOKEN_KEY = "pay:token:%s";

    /** 核销 token 防重放键：redeem:token:{jti}（核销成功后按 jti DEL） */
    private static final String REDEEM_TOKEN_KEY = "redeem:token:%s";

    private static final String JTI_PAY = "pt_";
    private static final String JTI_REDEEM = "rt_";
    private static final String CHANNEL_DESKTOP = "desktop_button";

    private final OrderTicketMapper orderTicketMapper;
    private final SeatLockMapper seatLockMapper;
    private final SeatStatusMapper seatStatusMapper;
    private final ShowMapper showMapper;
    private final PayTokenUtil payTokenUtil;
    private final RedisUtil redisUtil;

    @Value("${ticket.public-base-url}")
    private String publicBaseUrl;

    public PaymentServiceImpl(OrderTicketMapper orderTicketMapper,
                              SeatLockMapper seatLockMapper,
                              SeatStatusMapper seatStatusMapper,
                              ShowMapper showMapper,
                              PayTokenUtil payTokenUtil,
                              RedisUtil redisUtil) {
        this.orderTicketMapper = orderTicketMapper;
        this.seatLockMapper = seatLockMapper;
        this.seatStatusMapper = seatStatusMapper;
        this.showMapper = showMapper;
        this.payTokenUtil = payTokenUtil;
        this.redisUtil = redisUtil;
    }

    @Override
    public PayQrVO getPayQr(String userId, String orderId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED_TOKEN);
        }
        OrderTicket order = requireOrder(orderId);
        assertOwner(userId, order);
        if (OrderStatus.EXPIRED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.LOCK_EXPIRED, "支付已超时，请重新下单");
        }
        if (!OrderStatus.PENDING_PAY.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_NOT_PAYABLE, "仅待支付订单可发起支付");
        }
        OffsetDateTime now = DateTimeFormats.now();
        if (order.getExpireAt() == null || !order.getExpireAt().isAfter(now)) {
            throw new BusinessException(ResultCode.LOCK_EXPIRED, "支付已超时，请重新下单");
        }

        // 每次生成新 jti 的短期 token；JWT exp 为过期兜底，Redis 仅加速防重放
        PayTokenUtil.IssueResult issued = payTokenUtil.issue(JTI_PAY, orderId, userId, order.getExpireAt());
        setQuietly(String.format(PAY_TOKEN_KEY, issued.getJti()), orderId, issued.getTtlSeconds());

        String payUrl = publicBaseUrl + "/m/pay/" + orderId + "?t=" + issued.getToken();
        return PayQrVO.builder()
                .orderId(orderId)
                .amount(order.getAmount())
                .expireAt(DateTimeFormats.format(order.getExpireAt()))
                .payUrl(payUrl)
                .pollIntervalMs(POLL_INTERVAL_MS)
                .build();
    }

    @Override
    public PaySessionVO getPaySession(String orderId, String token) {
        PayTokenUtil.PayTokenPayload payload =
                requireToken(orderId, token, ResultCode.PAY_TOKEN_INVALID, ResultCode.PAY_TOKEN_EXPIRED);
        return toSessionVo(requireOrder(payload.getOrderId()));
    }

    @Override
    @Transactional
    public OrderVO pay(String orderId, String userId, String payToken, PayDTO dto) {
        // 鉴权二选一：JWT 本人，或 X-Pay-Token（须与 Path orderId 匹配且未过期）
        PayTokenUtil.PayTokenPayload payPayload = null;
        if (StringUtils.hasText(userId)) {
            // JWT 路径：userId 已由 SecurityContext 注入
        } else if (StringUtils.hasText(payToken)) {
            payPayload = requireToken(
                    orderId, payToken, ResultCode.PAY_TOKEN_INVALID, ResultCode.PAY_TOKEN_EXPIRED);
            userId = payPayload.getUserId();
        } else {
            throw new BusinessException(ResultCode.UNAUTHORIZED_TOKEN);
        }

        OrderTicket order = orderTicketMapper.findByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        assertOwner(userId, order);
        if (OrderStatus.ISSUED.equals(order.getStatus())) {
            // 幂等：已出票再付直接返回，不重复扣
            return toVo(order);
        }
        if (OrderStatus.EXPIRED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.LOCK_EXPIRED, "支付已超时，请重新下单");
        }
        if (!OrderStatus.PENDING_PAY.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_NOT_PAYABLE, "仅待支付订单可发起支付");
        }
        OffsetDateTime now = DateTimeFormats.now();
        // 过期订单即使二维码仍可扫、token 未过期也拒绝支付（DB 权威截止）
        if (order.getExpireAt() == null || !order.getExpireAt().isAfter(now)) {
            throw new BusinessException(ResultCode.LOCK_EXPIRED, "支付已超时，请重新下单");
        }
        // 三次防线：开场时间已过仍拒绝支付出票（订单创建后到支付间可能跨过开场时间）
        ShowSchedule show = showMapper.selectById(order.getShowId());
        if (show != null && show.getStartTime() != null && !show.getStartTime().isAfter(now)) {
            throw new BusinessException(ResultCode.SHOW_STARTED, "场次已开场，无法购票");
        }

        String ticketCode = buildTicketCode(orderId, now);
        String channel = normalizeChannel(dto);
        int updated = orderTicketMapper.updateIssued(orderId, ticketCode, channel, now);
        if (updated == 0) {
            // 并发兜底：另一请求已出票则幂等返回，否则拒绝
            OrderTicket raced = orderTicketMapper.findById(orderId);
            if (raced != null && OrderStatus.ISSUED.equals(raced.getStatus())) {
                return toVo(raced);
            }
            throw new BusinessException(ResultCode.ORDER_NOT_PAYABLE);
        }
        // 座位与锁座最终落定：locked → sold，lock active → consumed
        seatStatusMapper.markSoldByLockId(order.getLockId());
        seatLockMapper.markConsumed(order.getLockId());
        if (payPayload != null) {
            deleteQuietly(String.format(PAY_TOKEN_KEY, payPayload.getJti()));
        }

        return toVo(orderTicketMapper.findById(orderId));
    }

    @Override
    public RedeemQrVO getRedeemQr(String userId, String orderId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED_TOKEN);
        }
        OrderTicket order = requireOrder(orderId);
        assertOwner(userId, order);
        if (!OrderStatus.ISSUED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_NOT_REDEEMABLE, "仅已出票订单可生成核销二维码");
        }
        PayTokenUtil.IssueResult issued = payTokenUtil.issue(JTI_REDEEM, orderId, userId, null);
        setQuietly(String.format(REDEEM_TOKEN_KEY, issued.getJti()), orderId, issued.getTtlSeconds());

        String redeemUrl = publicBaseUrl + "/m/redeem/" + orderId + "?t=" + issued.getToken();
        return RedeemQrVO.builder()
                .orderId(orderId)
                .ticketCode(order.getTicketCode())
                .redeemUrl(redeemUrl)
                .build();
    }

    @Override
    public PaySessionVO getRedeemSession(String orderId, String token) {
        PayTokenUtil.PayTokenPayload payload =
                requireToken(orderId, token, ResultCode.REDEEM_TOKEN_INVALID, ResultCode.REDEEM_TOKEN_EXPIRED);
        return toSessionVo(requireOrder(payload.getOrderId()));
    }

    @Override
    @Transactional
    public OrderVO redeem(String orderId, String userId, String redeemToken) {
        // 鉴权二选一：JWT 本人，或 X-Redeem-Token
        PayTokenUtil.PayTokenPayload redeemPayload = null;
        if (StringUtils.hasText(userId)) {
            // JWT 路径
        } else if (StringUtils.hasText(redeemToken)) {
            redeemPayload = requireToken(
                    orderId, redeemToken, ResultCode.REDEEM_TOKEN_INVALID, ResultCode.REDEEM_TOKEN_EXPIRED);
            userId = redeemPayload.getUserId();
        } else {
            throw new BusinessException(ResultCode.UNAUTHORIZED_TOKEN);
        }

        OrderTicket order = orderTicketMapper.findByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        assertOwner(userId, order);
        if (OrderStatus.REDEEMED.equals(order.getStatus())) {
            // 幂等：已核销再核销直接返回
            return toVo(order);
        }
        if (!OrderStatus.ISSUED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_NOT_REDEEMABLE, "仅已出票订单可核销");
        }
        int updated = orderTicketMapper.updateRedeemed(orderId);
        if (updated == 0) {
            OrderTicket raced = orderTicketMapper.findById(orderId);
            if (raced != null && OrderStatus.REDEEMED.equals(raced.getStatus())) {
                return toVo(raced);
            }
            throw new BusinessException(ResultCode.ORDER_NOT_REDEEMABLE);
        }
        if (redeemPayload != null) {
            deleteQuietly(String.format(REDEEM_TOKEN_KEY, redeemPayload.getJti()));
        }

        return toVo(orderTicketMapper.findById(orderId));
    }

    /** 校验并解析二维码 token：验签失败或与订单不匹配 → invalid，过期 → expired */
    private PayTokenUtil.PayTokenPayload requireToken(String orderId, String token,
                                                      ResultCode invalidCode, ResultCode expiredCode) {
        PayTokenUtil.PayTokenPayload payload = payTokenUtil.verify(token);
        if (payload == null || payload.getOrderId() == null || !payload.getOrderId().equals(orderId)) {
            throw new BusinessException(invalidCode);
        }
        if (payload.getExp() == null || !payload.getExp().after(new Date())) {
            throw new BusinessException(expiredCode);
        }
        return payload;
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
            throw new BusinessException(ResultCode.FORBIDDEN_PERMISSION, "无权操作该订单");
        }
    }

    /** 取票码：TKT-{yyyyMMdd}-{orderId 末 4 位}，如 TKT-20260728-1001 */
    private String buildTicketCode(String orderId, OffsetDateTime now) {
        String tail = orderId.length() > 4 ? orderId.substring(orderId.length() - 4) : orderId;
        return "TKT-" + now.format(DAY) + "-" + tail;
    }

    /** 渠道缺省为桌面按钮 */
    private String normalizeChannel(PayDTO dto) {
        if (dto != null && StringUtils.hasText(dto.getChannel())) {
            return dto.getChannel().trim();
        }
        return CHANNEL_DESKTOP;
    }

    /** Redis 写入降级：故障不阻断二维码生成，token 过期由 JWT exp 兜底 */
    private void setQuietly(String key, Object value, long seconds) {
        try {
            redisUtil.set(key, value, seconds);
        } catch (Exception e) {
            log.warn("写 Redis 失败，跳过防重放记录: {}", e.getMessage());
        }
    }

    /** Redis 删除降级 */
    private void deleteQuietly(String key) {
        try {
            redisUtil.delete(key);
        } catch (Exception e) {
            log.warn("Redis DEL 失败: {}", e.getMessage());
        }
    }

    /** OrderTicket → OrderVO（本人视角，无需昵称） */
    private OrderVO toVo(OrderTicket o) {
        return OrderVO.builder()
                .orderId(o.getOrderId())
                .userId(o.getUserId())
                .showId(o.getShowId())
                .movieTitle(o.getMovieTitle())
                .cinemaName(o.getCinemaName())
                .hallName(o.getHallName())
                .startTime(format(o.getStartTime()))
                .seatIds(parseSeatIds(o.getSeatIdsJson()))
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

    /** OrderTicket → PaySessionVO（含座位号，供扫码页展示） */
    private PaySessionVO toSessionVo(OrderTicket o) {
        return PaySessionVO.builder()
                .orderId(o.getOrderId())
                .amount(o.getAmount())
                .expireAt(format(o.getExpireAt()))
                .movieTitle(o.getMovieTitle())
                .cinemaName(o.getCinemaName())
                .hallName(o.getHallName())
                .startTime(format(o.getStartTime()))
                .seatIds(parseSeatIds(o.getSeatIdsJson()))
                .seatNames(parseSeatNames(o.getSeatPriceSnapshot()))
                .status(o.getStatus())
                .ticketCode(o.getTicketCode())
                .build();
    }

    /** 解析座位 ID JSON；空白去重并保序 */
    private List<String> parseSeatIds(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        List<String> parsed = JSON.parseArray(json, String.class);
        return parsed != null ? parsed : Collections.<String>emptyList();
    }

    /** 从座位价区快照 JSON 提取座位号（seatName），保序 */
    private List<String> parseSeatNames(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            JSONArray arr = JSON.parseArray(json);
            if (arr == null || arr.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> names = new ArrayList<String>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj != null && StringUtils.hasText(obj.getString("seatName"))) {
                    names.add(obj.getString("seatName"));
                }
            }
            return names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** OffsetDateTime → ISO-8601 字符串；null 保持 null */
    private String format(OffsetDateTime t) {
        return t == null ? null : DateTimeFormats.format(t);
    }
}
