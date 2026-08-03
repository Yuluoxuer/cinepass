package com.cinepass.service;

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
import com.cinepass.model.OrderTicket;
import com.cinepass.model.SeatLock;
import com.cinepass.model.SeatPriceRow;
import com.cinepass.model.ShowSnapshot;
import com.cinepass.security.Roles;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.impl.OrderServiceImpl;
import com.cinepass.vo.OrderVO;
import com.cinepass.vo.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderService 边界单元测试（Mockito，模式 D）。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String USER = "u_owner";
    private static final String OTHER = "u_other";
    private static final String LOCK = "lk_1";
    private static final String SHOW = "s_1";
    private static final String ORDER = "o_1";
    private static final ZoneOffset CST = ZoneOffset.ofHours(8);

    @Mock
    private OrderTicketMapper orderTicketMapper;
    @Mock
    private SeatLockMapper seatLockMapper;
    @Mock
    private SeatStatusMapper seatStatusMapper;
    @Mock
    private ShowScheduleMapper showScheduleMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void clearCtx() {
        SecurityContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    // ---------- create ----------

    @Test
    void create_blankUserId_shouldUnauthorized() {
        CreateOrderDTO dto = dto(LOCK);
        assertBiz(() -> orderService.create("  ", dto), ResultCode.UNAUTHORIZED_TOKEN);
        assertBiz(() -> orderService.create(null, dto), ResultCode.UNAUTHORIZED_TOKEN);
    }

    @Test
    void create_lockMissing_shouldLockExpired() {
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(null);
        assertBiz(() -> orderService.create(USER, dto(LOCK)), ResultCode.LOCK_EXPIRED);
    }

    @Test
    void create_lockOwnedByOther_shouldForbidden() {
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(activeLock(OTHER));
        assertBiz(() -> orderService.create(USER, dto(LOCK)), ResultCode.FORBIDDEN_PERMISSION);
    }

    @Test
    void create_lockReleased_shouldLockExpired() {
        SeatLock lock = activeLock(USER);
        lock.setStatus(LockStatus.RELEASED);
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(lock);
        assertBiz(() -> orderService.create(USER, dto(LOCK)), ResultCode.LOCK_EXPIRED);
    }

    @Test
    void create_lockExpireAtExactNow_shouldLockExpired() {
        SeatLock lock = activeLock(USER);
        // isAfter(now) 为 false 时边界：expireAt == now 不可下单
        lock.setExpireAt(OffsetDateTime.now(CST).minusSeconds(1));
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(lock);
        assertBiz(() -> orderService.create(USER, dto(LOCK)), ResultCode.LOCK_EXPIRED);
    }

    @Test
    void create_lockExpireAtNull_shouldLockExpired() {
        SeatLock lock = activeLock(USER);
        lock.setExpireAt(null);
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(lock);
        assertBiz(() -> orderService.create(USER, dto(LOCK)), ResultCode.LOCK_EXPIRED);
    }

    @Test
    void create_idempotentWhenOrderExists_shouldReturnExistingWithoutInsert() {
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(activeLock(USER));
        OrderTicket existing = pendingOrder(USER);
        when(orderTicketMapper.findByLockId(LOCK)).thenReturn(existing);

        OrderVO vo = orderService.create(USER, dto(LOCK));
        assertThat(vo.getOrderId()).isEqualTo(ORDER);
        verify(orderTicketMapper, never()).insert(any(OrderTicket.class));
        verify(showScheduleMapper, never()).findSnapshot(anyString());
    }

    @Test
    void create_showMissing_shouldNotFound() {
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(activeLock(USER));
        when(orderTicketMapper.findByLockId(LOCK)).thenReturn(null);
        when(showScheduleMapper.findSnapshot(SHOW)).thenReturn(null);
        assertBiz(() -> orderService.create(USER, dto(LOCK)), ResultCode.NOT_FOUND);
    }

    @Test
    void create_emptySeatIds_shouldParamError() {
        SeatLock lock = activeLock(USER);
        lock.setSeatIdsJson("[]");
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(lock);
        when(orderTicketMapper.findByLockId(LOCK)).thenReturn(null);
        when(showScheduleMapper.findSnapshot(SHOW)).thenReturn(snapshot());
        assertBiz(() -> orderService.create(USER, dto(LOCK)), ResultCode.PARAM_ERROR);
    }

    @Test
    void create_blankSeatIdsJson_shouldParamError() {
        SeatLock lock = activeLock(USER);
        lock.setSeatIdsJson("   ");
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(lock);
        when(orderTicketMapper.findByLockId(LOCK)).thenReturn(null);
        when(showScheduleMapper.findSnapshot(SHOW)).thenReturn(snapshot());
        assertBiz(() -> orderService.create(USER, dto(LOCK)), ResultCode.PARAM_ERROR);
    }

    @Test
    void create_incompleteSeatPrices_shouldParamError() {
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(activeLock(USER));
        when(orderTicketMapper.findByLockId(LOCK)).thenReturn(null);
        when(showScheduleMapper.findSnapshot(SHOW)).thenReturn(snapshot());
        // 锁了 2 座，只返回 1 行价区
        when(showScheduleMapper.listSeatPrices(eq(SHOW), anyList()))
                .thenReturn(Collections.singletonList(priceRow("sm:1:1", "55.00")));
        assertBiz(() -> orderService.create(USER, dto(LOCK)), ResultCode.PARAM_ERROR);
    }

    @Test
    void create_duplicateKeyRace_shouldReturnRacedOrder() {
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(activeLock(USER));
        when(orderTicketMapper.findByLockId(LOCK)).thenReturn(null).thenReturn(pendingOrder(USER));
        when(showScheduleMapper.findSnapshot(SHOW)).thenReturn(snapshot());
        when(showScheduleMapper.listSeatPrices(eq(SHOW), anyList()))
                .thenReturn(Arrays.asList(
                        priceRow("sm:1:1", "55.00"),
                        priceRow("sm:1:2", "55.00")));
        when(orderTicketMapper.insert(any(OrderTicket.class)))
                .thenThrow(new DuplicateKeyException("uk_lock"));

        OrderVO vo = orderService.create(USER, dto("  " + LOCK + "  "));
        assertThat(vo.getOrderId()).isEqualTo(ORDER);
        assertThat(vo.getAmount()).isEqualByComparingTo("110.00");
    }

    @Test
    void create_happyPath_shouldSumAmountAndPreferDtoSession() {
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(activeLock(USER));
        when(orderTicketMapper.findByLockId(LOCK)).thenReturn(null);
        when(showScheduleMapper.findSnapshot(SHOW)).thenReturn(snapshot());
        when(showScheduleMapper.listSeatPrices(eq(SHOW), anyList()))
                .thenReturn(Arrays.asList(
                        priceRow("sm:1:1", "40.00"),
                        priceRow("sm:1:2", "60.50")));
        when(orderTicketMapper.insert(any(OrderTicket.class))).thenReturn(1);

        CreateOrderDTO dto = dto(LOCK);
        dto.setSessionId("sess_from_client");
        OrderVO vo = orderService.create(USER, dto);

        assertThat(vo.getOrderId()).startsWith("o");
        assertThat(vo.getAmount()).isEqualByComparingTo("100.50");
        assertThat(vo.getUnitPrice()).isEqualByComparingTo("50.25");
        assertThat(vo.getStatus()).isEqualTo(OrderStatus.PENDING_PAY);
        assertThat(vo.getSeatIds()).containsExactly("sm:1:1", "sm:1:2");
        verify(orderTicketMapper).insert(any(OrderTicket.class));
    }

    @Test
    void create_dedupeSeatIdsInJson_shouldUseUniqueCountForPrices() {
        SeatLock lock = activeLock(USER);
        lock.setSeatIdsJson("[\"sm:1:1\",\"sm:1:1\",\" sm:1:2 \"]");
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(lock);
        when(orderTicketMapper.findByLockId(LOCK)).thenReturn(null);
        when(showScheduleMapper.findSnapshot(SHOW)).thenReturn(snapshot());
        when(showScheduleMapper.listSeatPrices(eq(SHOW), anyList()))
                .thenReturn(Arrays.asList(
                        priceRow("sm:1:1", "10.00"),
                        priceRow("sm:1:2", "20.00")));
        when(orderTicketMapper.insert(any(OrderTicket.class))).thenReturn(1);

        OrderVO vo = orderService.create(USER, dto(LOCK));
        assertThat(vo.getSeatIds()).containsExactly("sm:1:1", "sm:1:2");
        assertThat(vo.getAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void create_nullSeatPrice_shouldFallbackToBasePrice() {
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(activeLock(USER));
        when(orderTicketMapper.findByLockId(LOCK)).thenReturn(null);
        when(showScheduleMapper.findSnapshot(SHOW)).thenReturn(snapshot());
        SeatPriceRow nullPrice = priceRow("sm:1:1", "40.00");
        nullPrice.setPrice(null);
        SeatPriceRow priced = priceRow("sm:1:2", "60.00");
        when(showScheduleMapper.listSeatPrices(eq(SHOW), anyList()))
                .thenReturn(Arrays.asList(nullPrice, priced));
        when(orderTicketMapper.insert(any(OrderTicket.class))).thenReturn(1);

        OrderVO vo = orderService.create(USER, dto(LOCK));
        // base 50 + 60 = 110
        assertThat(vo.getAmount()).isEqualByComparingTo("110.00");
    }

    @Test
    void create_duplicateKeyRace_andNoRacedRow_shouldRethrow() {
        when(seatLockMapper.findByIdForUpdate(LOCK)).thenReturn(activeLock(USER));
        when(orderTicketMapper.findByLockId(LOCK)).thenReturn(null);
        when(showScheduleMapper.findSnapshot(SHOW)).thenReturn(snapshot());
        when(showScheduleMapper.listSeatPrices(eq(SHOW), anyList()))
                .thenReturn(Arrays.asList(
                        priceRow("sm:1:1", "55.00"),
                        priceRow("sm:1:2", "55.00")));
        when(orderTicketMapper.insert(any(OrderTicket.class)))
                .thenThrow(new DuplicateKeyException("uk_lock"));

        assertThatThrownBy(() -> orderService.create(USER, dto(LOCK)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ---------- get / list ----------

    @Test
    void getMine_missing_shouldNotFound() {
        when(orderTicketMapper.findById(ORDER)).thenReturn(null);
        assertBiz(() -> orderService.getMine(USER, ORDER), ResultCode.NOT_FOUND);
    }

    @Test
    void getMine_otherOwner_shouldForbidden() {
        when(orderTicketMapper.findById(ORDER)).thenReturn(pendingOrder(OTHER));
        assertBiz(() -> orderService.getMine(USER, ORDER), ResultCode.FORBIDDEN_PERMISSION);
    }

    @Test
    void listMine_illegalStatus_shouldParamError() {
        assertBiz(() -> orderService.listMine(USER, "paid", 1, 20), ResultCode.PARAM_ERROR);
        assertBiz(() -> orderService.listMine(USER, "PENDING_PAY", 1, 20), ResultCode.PARAM_ERROR);
    }

    @Test
    void listMine_pageSizeBoundaries_shouldNormalize() {
        when(orderTicketMapper.countByUser(eq(USER), isNull())).thenReturn(0L);

        PageResult<OrderVO> p0 = orderService.listMine(USER, null, 0, 0);
        assertThat(p0.getPage()).isEqualTo(1);
        assertThat(p0.getSize()).isEqualTo(20);
        assertThat(p0.getTotal()).isZero();

        PageResult<OrderVO> pHuge = orderService.listMine(USER, "  ", -3, 999);
        assertThat(pHuge.getPage()).isEqualTo(1);
        assertThat(pHuge.getSize()).isEqualTo(50);
    }

    @Test
    void listMine_validStatus_shouldPassThrough() {
        when(orderTicketMapper.countByUser(USER, OrderStatus.PENDING_PAY)).thenReturn(1L);
        when(orderTicketMapper.listByUser(eq(USER), eq(OrderStatus.PENDING_PAY), eq(0), eq(10)))
                .thenReturn(Collections.singletonList(pendingOrder(USER)));

        PageResult<OrderVO> page = orderService.listMine(USER, OrderStatus.PENDING_PAY, 1, 10);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).getOrderId()).isEqualTo(ORDER);
    }

    // ---------- cancel ----------

    @Test
    void cancel_missing_shouldNotFound() {
        when(orderTicketMapper.findByIdForUpdate(ORDER)).thenReturn(null);
        assertBiz(() -> orderService.cancel(USER, ORDER, null), ResultCode.NOT_FOUND);
    }

    @Test
    void cancel_otherOwner_shouldForbidden() {
        when(orderTicketMapper.findByIdForUpdate(ORDER)).thenReturn(pendingOrder(OTHER));
        assertBiz(() -> orderService.cancel(USER, ORDER, null), ResultCode.FORBIDDEN_PERMISSION);
    }

    @Test
    void cancel_alreadyCancelled_shouldIdempotentNoRelease() {
        OrderTicket cancelled = pendingOrder(USER);
        cancelled.setStatus(OrderStatus.CANCELLED);
        when(orderTicketMapper.findByIdForUpdate(ORDER)).thenReturn(cancelled);

        OrderVO vo = orderService.cancel(USER, ORDER, null);
        assertThat(vo.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderTicketMapper, never()).updateCancelled(anyString(), anyString());
        verify(seatLockMapper, never()).markReleased(anyString());
        verify(seatStatusMapper, never()).releaseByLockId(anyString());
    }

    @Test
    void cancel_issued_shouldNotCancellable() {
        OrderTicket issued = pendingOrder(USER);
        issued.setStatus(OrderStatus.ISSUED);
        when(orderTicketMapper.findByIdForUpdate(ORDER)).thenReturn(issued);
        assertBiz(() -> orderService.cancel(USER, ORDER, new CancelOrderDTO()),
                ResultCode.ORDER_NOT_CANCELLABLE);
    }

    @Test
    void cancel_updateZeroRows_shouldNotCancellable() {
        when(orderTicketMapper.findByIdForUpdate(ORDER)).thenReturn(pendingOrder(USER));
        when(orderTicketMapper.updateCancelled(eq(ORDER), anyString())).thenReturn(0);
        assertBiz(() -> orderService.cancel(USER, ORDER, null), ResultCode.ORDER_NOT_CANCELLABLE);
    }

    @Test
    void cancel_pending_shouldReleaseLockAndDefaultReason() {
        when(orderTicketMapper.findByIdForUpdate(ORDER)).thenReturn(pendingOrder(USER));
        when(orderTicketMapper.updateCancelled(ORDER, "user_cancel")).thenReturn(1);
        OrderTicket after = pendingOrder(USER);
        after.setStatus(OrderStatus.CANCELLED);
        when(orderTicketMapper.findById(ORDER)).thenReturn(after);

        OrderVO vo = orderService.cancel(USER, ORDER, null);
        assertThat(vo.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(seatLockMapper).markReleased(LOCK);
        verify(seatStatusMapper).releaseByLockId(LOCK);
    }

    @Test
    void cancel_withCustomReason_shouldTrimReason() {
        when(orderTicketMapper.findByIdForUpdate(ORDER)).thenReturn(pendingOrder(USER));
        when(orderTicketMapper.updateCancelled(ORDER, "timeout")).thenReturn(1);
        when(orderTicketMapper.findById(ORDER)).thenReturn(pendingOrder(USER));

        CancelOrderDTO dto = new CancelOrderDTO();
        dto.setReason("  timeout  ");
        orderService.cancel(USER, ORDER, dto);
        verify(orderTicketMapper).updateCancelled(ORDER, "timeout");
    }

    // ---------- listAdmin ----------

    @Test
    void listAdmin_userRole_shouldForbidden() {
        SecurityContext.set(USER, "u", null, null,
                Collections.singletonList(Roles.USER), null);
        assertBiz(() -> orderService.listAdmin(null, null, null, null, 1, 20),
                ResultCode.FORBIDDEN_PERMISSION);
    }

    @Test
    void listAdmin_staffWithoutCinema_shouldForbidden() {
        SecurityContext.set(USER, "staff", null, null,
                Collections.singletonList(Roles.STAFF), null);
        assertBiz(() -> orderService.listAdmin(null, null, null, null, 1, 20),
                ResultCode.FORBIDDEN_PERMISSION);
    }

    @Test
    void listAdmin_staffWithCinema_shouldScopeByCinema() {
        SecurityContext.set(USER, "staff", null, null,
                Collections.singletonList(Roles.STAFF), null);
        SecurityContext.setCinemaId("c12");
        when(orderTicketMapper.countAdmin(isNull(), isNull(), isNull(), isNull(), eq("c12")))
                .thenReturn(0L);

        PageResult<OrderVO> page = orderService.listAdmin(null, null, null, null, 1, 20);
        assertThat(page.getTotal()).isZero();
        verify(orderTicketMapper).countAdmin(isNull(), isNull(), isNull(), isNull(), eq("c12"));
    }

    @Test
    void listAdmin_admin_shouldNotFilterCinema() {
        SecurityContext.set(USER, "admin", null, null,
                Collections.singletonList(Roles.ADMIN), null);
        when(orderTicketMapper.countAdmin(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(0L);

        orderService.listAdmin(null, null, null, null, 1, 20);
        verify(orderTicketMapper).countAdmin(isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void listAdmin_illegalStatus_shouldParamError() {
        SecurityContext.set(USER, "admin", null, null,
                Collections.singletonList(Roles.ADMIN), null);
        assertBiz(() -> orderService.listAdmin(null, "xxx", null, null, 1, 20),
                ResultCode.PARAM_ERROR);
    }

    // ---------- helpers ----------

    private static CreateOrderDTO dto(String lockId) {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setLockId(lockId);
        return dto;
    }

    private static SeatLock activeLock(String userId) {
        SeatLock lock = new SeatLock();
        lock.setLockId(LOCK);
        lock.setShowId(SHOW);
        lock.setUserId(userId);
        lock.setSeatIdsJson("[\"sm:1:1\",\"sm:1:2\"]");
        lock.setStatus(LockStatus.ACTIVE);
        lock.setExpireAt(OffsetDateTime.now(CST).plusMinutes(10));
        lock.setSessionId("sess_lock");
        return lock;
    }

    private static ShowSnapshot snapshot() {
        ShowSnapshot snap = new ShowSnapshot();
        snap.setShowId(SHOW);
        snap.setMovieTitle("片");
        snap.setCinemaName("影城");
        snap.setHallName("1号厅");
        snap.setStartTime(OffsetDateTime.now(CST).plusHours(2));
        snap.setBasePrice(new BigDecimal("50.00"));
        return snap;
    }

    private static SeatPriceRow priceRow(String seatId, String price) {
        SeatPriceRow row = new SeatPriceRow();
        row.setSeatId(seatId);
        row.setZone("normal");
        row.setSeatName(seatId);
        row.setPrice(new BigDecimal(price));
        return row;
    }

    private static OrderTicket pendingOrder(String userId) {
        OrderTicket o = new OrderTicket();
        o.setOrderId(ORDER);
        o.setUserId(userId);
        o.setShowId(SHOW);
        o.setLockId(LOCK);
        o.setSeatIdsJson("[\"sm:1:1\",\"sm:1:2\"]");
        o.setUnitPrice(new BigDecimal("55.00"));
        o.setAmount(new BigDecimal("110.00"));
        o.setStatus(OrderStatus.PENDING_PAY);
        o.setExpireAt(OffsetDateTime.now(CST).plusMinutes(10));
        o.setCreatedAt(OffsetDateTime.now(CST));
        return o;
    }

    private static void assertBiz(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(code.getCode()));
    }
}
