package com.cinepass.service.impl;

import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.ShowBatchCreateDTO;
import com.cinepass.dto.ShowCreateDTO;
import com.cinepass.dto.ShowUpdateDTO;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.HallMapper;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.SeatMapMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Hall;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
import com.cinepass.model.UserAccount;
import com.cinepass.security.Roles;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.AdminShowService;
import com.cinepass.service.EsIndexService;
import com.cinepass.service.SeatInventoryService;
import com.cinepass.service.ShowService;
import com.cinepass.util.ShowIds;
import com.cinepass.util.DateTimeFormats;
import com.cinepass.vo.ShowVO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AdminShowService} 实现。
 * <p>同厅排片冲突：事务内锁定影厅行后再查 overlap；库侧 EXCLUDE 约束兜底并发。
 * 对外错误文案提示 {@link #SHOW_BUFFER_MINUTES} 分钟清场缓冲。
 */
@Service
public class AdminShowServiceImpl implements AdminShowService {

    /** 清场缓冲分钟数（冲突提示与系分约定对齐） */
    private static final int SHOW_BUFFER_MINUTES = 20;

    private final ShowMapper showMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final SeatMapMapper seatMapMapper;
    private final ShowService showService;
    private final SeatInventoryService seatInventoryService;
    private final EsIndexService esIndexService;
    private final UserAccountMapper userAccountMapper;

    public AdminShowServiceImpl(ShowMapper showMapper,
                                MovieMapper movieMapper,
                                CinemaMapper cinemaMapper,
                                HallMapper hallMapper,
                                SeatMapMapper seatMapMapper,
                                ShowService showService,
                                SeatInventoryService seatInventoryService,
                                EsIndexService esIndexService,
                                UserAccountMapper userAccountMapper) {
        this.showMapper = showMapper;
        this.movieMapper = movieMapper;
        this.cinemaMapper = cinemaMapper;
        this.hallMapper = hallMapper;
        this.seatMapMapper = seatMapMapper;
        this.showService = showService;
        this.seatInventoryService = seatInventoryService;
        this.esIndexService = esIndexService;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    @Transactional
    public ShowVO create(ShowCreateDTO dto) {
        assertCinemaScope(dto.getCinemaId());
        Movie movie = movieMapper.selectById(dto.getMovieId());
        if (movie == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        if ("off".equals(movie.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "影片《" + movie.getTitle() + "》已下架，不能创建场次");
        }
        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影院不存在");
        }
        Hall hall = requireHallForUpdate(dto.getHallId(), dto.getCinemaId());
        OffsetDateTime startTime = OffsetDateTime.parse(dto.getStartTime());
        OffsetDateTime endTime = OffsetDateTime.parse(dto.getEndTime());
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "散场时间必须晚于开场时间");
        }
        if (startTime.isBefore(DateTimeFormats.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不允许创建过去时间的场次");
        }
        assertNoHallConflict(hall.getHallId(), startTime, endTime, null);

        BigDecimal showPrice = computePrice(dto.getZonePrices(), dto.getPrice());
        OffsetDateTime now = DateTimeFormats.now();
        ShowSchedule show = new ShowSchedule();
        show.setShowId(ShowIds.next());
        show.setMovieId(dto.getMovieId());
        show.setCinemaId(dto.getCinemaId());
        show.setHallId(dto.getHallId());
        show.setSeatMapId(hall.getSeatMapId());
        show.setStartTime(startTime);
        show.setEndTime(endTime);
        show.setPrice(showPrice);
        show.setStatus("on_sale");
        show.setCreatedAt(now);
        show.setUpdatedAt(now);
        insertShowGuarded(show);
        if (StringUtils.hasText(show.getSeatMapId())) {
            seatMapMapper.markImmutable(show.getSeatMapId());
        }
        seatInventoryService.ensureSeatStatus(show.getShowId(), show.getSeatMapId());
        esIndexService.syncCinema(show.getCinemaId());

        ShowSchedule saved = showMapper.selectById(show.getShowId());
        return showService.buildShowVO(saved, toZonePriceVOs(dto.getZonePrices(), showPrice));
    }

    @Override
    @Transactional
    public List<ShowVO> batchCreate(ShowBatchCreateDTO dto) {
        assertCinemaScope(dto.getCinemaId());
        Movie movie = movieMapper.selectById(dto.getMovieId());
        if (movie == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        if ("off".equals(movie.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "影片《" + movie.getTitle() + "》已下架，不能创建场次");
        }
        if (movie.getDurationMin() == null || movie.getDurationMin() <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "影片时长信息缺失");
        }
        int durationMin = movie.getDurationMin();
        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影院不存在");
        }
        // 整批共用同一影厅锁，避免批内/批间并发双写
        Hall hall = requireHallForUpdate(dto.getHallId(), dto.getCinemaId());

        if (dto.getIntervalMin() < durationMin) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "场次间隔（" + dto.getIntervalMin() + " 分钟）不得小于影片时长（"
                            + durationMin + " 分钟）");
        }

        LocalDate dateStart = LocalDate.parse(dto.getDateStart(), DateTimeFormats.DATE);
        LocalDate dateEnd = LocalDate.parse(dto.getDateEnd(), DateTimeFormats.DATE);
        if (dateEnd.isBefore(dateStart)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束日期必须不早于开始日期");
        }

        LocalTime timeStart = LocalTime.parse(dto.getTimeStart(), DateTimeFormats.TIME_HM);
        LocalTime timeEnd = LocalTime.parse(dto.getTimeEnd(), DateTimeFormats.TIME_HM);
        if (!timeEnd.isAfter(timeStart)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "每日结束时间必须晚于开始时间");
        }

        BigDecimal showPrice = computePrice(dto.getZonePrices(), dto.getPrice());
        OffsetDateTime now = DateTimeFormats.now();
        int intervalMin = dto.getIntervalMin();
        List<ShowVO> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (LocalDate date = dateStart; !date.isAfter(dateEnd); date = date.plusDays(1)) {
            LocalTime cursor = timeStart;
            while (true) {
                OffsetDateTime startTime = OffsetDateTime.of(date, cursor, DateTimeFormats.OFFSET);
                OffsetDateTime endTime = startTime.plusMinutes(durationMin);

                if (endTime.toLocalTime().isAfter(timeEnd)) {
                    break;
                }
                if (startTime.isBefore(now)) {
                    cursor = cursor.plusMinutes(intervalMin);
                    continue;
                }

                List<ShowSchedule> overlaps = showMapper.findOverlapping(
                        hall.getHallId(), startTime, endTime, null);
                if (overlaps != null && !overlaps.isEmpty()) {
                    String label = date + " " + cursor;
                    errors.add(label + " 与已有场次冲突，已跳过");
                    cursor = cursor.plusMinutes(intervalMin);
                    continue;
                }

                ShowSchedule show = new ShowSchedule();
                show.setShowId(ShowIds.next());
                show.setMovieId(dto.getMovieId());
                show.setCinemaId(dto.getCinemaId());
                show.setHallId(dto.getHallId());
                show.setSeatMapId(hall.getSeatMapId());
                show.setStartTime(startTime);
                show.setEndTime(endTime);
                show.setPrice(showPrice);
                show.setStatus("on_sale");
                show.setCreatedAt(now);
                show.setUpdatedAt(now);
                try {
                    insertShowGuarded(show);
                } catch (BusinessException ex) {
                    if (ex.getCode() != null && ex.getCode() == ResultCode.CONFLICT.getCode()) {
                        errors.add(date + " " + cursor + " 与已有场次冲突，已跳过");
                        cursor = cursor.plusMinutes(intervalMin);
                        continue;
                    }
                    throw ex;
                }

                if (StringUtils.hasText(show.getSeatMapId())) {
                    seatMapMapper.markImmutable(show.getSeatMapId());
                }
                seatInventoryService.ensureSeatStatus(show.getShowId(), show.getSeatMapId());

                ShowSchedule saved = showMapper.selectById(show.getShowId());
                created.add(showService.buildShowVO(saved,
                        toZonePriceVOs(dto.getZonePrices(), showPrice)));

                cursor = cursor.plusMinutes(intervalMin);
            }
        }

        esIndexService.syncCinema(dto.getCinemaId());

        if (created.isEmpty() && !errors.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "批量创建失败：" + String.join("；", errors));
        }

        return created;
    }

    @Override
    @Transactional
    public ShowVO update(String showId, ShowUpdateDTO dto) {
        ShowSchedule show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        assertCinemaScope(show.getCinemaId());
        if (!"on_sale".equals(show.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅可修改在售场次");
        }
        int activeLocks = showMapper.countActiveLocksOrOrders(showId);
        if (activeLocks > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "场次存在有效锁座，无法修改");
        }
        OffsetDateTime newStart = dto.getStartTime() != null
                ? OffsetDateTime.parse(dto.getStartTime()) : show.getStartTime();
        OffsetDateTime newEnd = dto.getEndTime() != null
                ? OffsetDateTime.parse(dto.getEndTime()) : show.getEndTime();
        if (!newEnd.isAfter(newStart)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "散场时间必须晚于开场时间");
        }
        requireHallForUpdate(show.getHallId(), show.getCinemaId());
        assertNoHallConflict(show.getHallId(), newStart, newEnd, showId);
        show.setStartTime(newStart);
        show.setEndTime(newEnd);
        if (dto.getPrice() != null || dto.getZonePrices() != null) {
            show.setPrice(computePrice(dto.getZonePrices(), dto.getPrice()));
        }
        show.setUpdatedAt(DateTimeFormats.now());
        try {
            showMapper.update(show);
        } catch (DataIntegrityViolationException ex) {
            throw hallTimeConflict(ex);
        }
        esIndexService.syncCinema(show.getCinemaId());
        ShowSchedule updated = showMapper.selectById(showId);
        return showService.buildShowVO(updated, toZonePriceVOs(dto.getZonePrices(), updated.getPrice()));
    }

    @Override
    @Transactional
    public ShowVO cancel(String showId) {
        ShowSchedule show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        assertCinemaScope(show.getCinemaId());
        if ("cancelled".equals(show.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "场次已取消");
        }
        showMapper.cancel(showId);
        esIndexService.syncCinema(show.getCinemaId());
        ShowSchedule updated = showMapper.selectById(showId);
        return showService.buildShowVO(updated, null);
    }

    @Override
    @Transactional
    public ShowVO closeSale(String showId) {
        ShowSchedule show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        assertCinemaScope(show.getCinemaId());
        if (!"on_sale".equals(show.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "该场次当前不可停售");
        }
        showMapper.closeSale(showId);
        esIndexService.syncCinema(show.getCinemaId());
        ShowSchedule updated = showMapper.selectById(showId);
        return showService.buildShowVO(updated, null);
    }

    @Override
    @Transactional
    public ShowVO resumeSale(String showId) {
        ShowSchedule show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        assertCinemaScope(show.getCinemaId());
        if (!"off_sale".equals(show.getStatus()) && !"cancelled".equals(show.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅可恢复已停售/取消的场次");
        }
        requireHallForUpdate(show.getHallId(), show.getCinemaId());
        assertNoHallConflict(show.getHallId(), show.getStartTime(), show.getEndTime(), showId);
        try {
            showMapper.resumeSale(showId);
        } catch (DataIntegrityViolationException ex) {
            throw hallTimeConflict(ex);
        }
        esIndexService.syncCinema(show.getCinemaId());
        ShowSchedule updated = showMapper.selectById(showId);
        return showService.buildShowVO(updated, null);
    }

    /** 锁定影厅行并校验归属，供冲突检测与写库串行化 */
    private Hall requireHallForUpdate(String hallId, String cinemaId) {
        Hall hall = hallMapper.selectByIdForUpdate(hallId);
        if (hall == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影厅不存在");
        }
        if (!hall.getCinemaId().equals(cinemaId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "影厅不属于所选影院");
        }
        return hall;
    }

    /** 同厅时段 + 清场缓冲冲突则抛 CONFLICT */
    private void assertNoHallConflict(String hallId, OffsetDateTime start, OffsetDateTime end,
                                      String excludeShowId) {
        List<ShowSchedule> overlaps = showMapper.findOverlapping(hallId, start, end, excludeShowId);
        if (overlaps != null && !overlaps.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "与场次 " + overlaps.get(0).getShowId() + " 时间冲突，需预留"
                            + SHOW_BUFFER_MINUTES + " 分钟缓冲");
        }
    }

    private void insertShowGuarded(ShowSchedule show) {
        try {
            showMapper.insert(show);
        } catch (DataIntegrityViolationException ex) {
            throw hallTimeConflict(ex);
        }
    }

    private BusinessException hallTimeConflict(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (detail != null && detail.contains("show_hall_time_excl")) {
            return new BusinessException(ResultCode.CONFLICT,
                    "同影厅时段冲突，需预留" + SHOW_BUFFER_MINUTES + " 分钟缓冲");
        }
        return new BusinessException(ResultCode.CONFLICT, "排片写入冲突，请重试");
    }

    /** 有分区价取最低价落库；否则用统一价兜底 */
    private BigDecimal computePrice(List<ShowCreateDTO.ZonePriceItem> zonePrices, BigDecimal fallback) {
        if (zonePrices != null && !zonePrices.isEmpty()) {
            return zonePrices.stream()
                    .map(ShowCreateDTO.ZonePriceItem::getPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(fallback != null ? fallback : BigDecimal.ZERO);
        }
        if (fallback != null && fallback.compareTo(BigDecimal.ZERO) > 0) {
            return fallback;
        }
        return BigDecimal.ZERO;
    }

    private List<ShowVO.ZonePriceVO> toZonePriceVOs(List<ShowCreateDTO.ZonePriceItem> items, BigDecimal fallback) {
        if (items == null || items.isEmpty()) return null;
        List<ShowVO.ZonePriceVO> vos = new ArrayList<>();
        for (ShowCreateDTO.ZonePriceItem item : items) {
            vos.add(ShowVO.ZonePriceVO.builder()
                    .zone(item.getZone())
                    .price(item.getPrice() != null ? item.getPrice().setScale(2, RoundingMode.HALF_UP) : fallback)
                    .build());
        }
        return vos;
    }

    /** staff 只能操作自己绑定的影院；admin 不受限 */
    private void assertCinemaScope(String cinemaId) {
        if (SecurityContext.isAdmin()) return;
        if (!SecurityContext.hasRole(Roles.STAFF) || !currentStaffCinemaId().equals(cinemaId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该影院");
        }
    }

    private String currentStaffCinemaId() {
        String cinemaIdFromToken = SecurityContext.getCurrentCinemaId();
        if (StringUtils.hasText(cinemaIdFromToken)) return cinemaIdFromToken;
        UserAccount user = userAccountMapper.findById(SecurityContext.getCurrentUserId());
        if (user == null || !StringUtils.hasText(user.getCinemaId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "工作人员未绑定影院");
        }
        return user.getCinemaId();
    }
}
