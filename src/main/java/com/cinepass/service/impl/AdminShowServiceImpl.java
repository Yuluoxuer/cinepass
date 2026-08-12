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
import com.cinepass.vo.ShowVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AdminShowService} 实现。
 * <p>同厅排片冲突检测依赖 Mapper；对外错误文案提示 {@link #SHOW_BUFFER_MINUTES} 分钟清场缓冲。
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

    // 创建单个场次：权限校验→校验影片(存在+未下架)/影院/影厅→校验时间合法性→冲突检测→落库→锁定座位图→播种座位→同步ES
    @Override
    @Transactional
    public ShowVO create(ShowCreateDTO dto) {
        assertCinemaScope(dto.getCinemaId());
        // 校验影片存在且未下架
        Movie movie = movieMapper.selectById(dto.getMovieId());
        if (movie == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        if ("off".equals(movie.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "影片《" + movie.getTitle() + "》已下架，不能创建场次");
        }
        // 校验影院存在
        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影院不存在");
        }
        // 校验影厅存在且归属正确
        Hall hall = hallMapper.selectById(dto.getHallId());
        if (hall == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影厅不存在");
        }
        if (!hall.getCinemaId().equals(dto.getCinemaId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "影厅不属于所选影院");
        }
        // 校验时间：散场晚于开场、不能创建过去场次
        OffsetDateTime startTime = OffsetDateTime.parse(dto.getStartTime());
        OffsetDateTime endTime = OffsetDateTime.parse(dto.getEndTime());
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "散场时间必须晚于开场时间");
        }
        if (startTime.isBefore(OffsetDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不允许创建过去时间的场次");
        }
        // 同厅冲突检测：查询与已有场次是否时间重叠（需预留缓冲分钟数）
        List<ShowSchedule> overlaps = showMapper.findOverlapping(
                hall.getHallId(), startTime, endTime, null);
        if (overlaps != null && !overlaps.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "与场次 " + overlaps.get(0).getShowId() + " 时间冲突，需预留"
                            + SHOW_BUFFER_MINUTES + " 分钟缓冲");
        }
        // 构造场次实体并落库
        BigDecimal showPrice = computePrice(dto.getZonePrices(), dto.getPrice());
        OffsetDateTime now = OffsetDateTime.now();
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
        showMapper.insert(show);
        // 排片后锁定座位图模板不可再改布局，并播种座位状态
        if (StringUtils.hasText(show.getSeatMapId())) {
            seatMapMapper.markImmutable(show.getSeatMapId());
        }
        seatInventoryService.ensureSeatStatus(show.getShowId(), show.getSeatMapId());
        esIndexService.syncCinema(show.getCinemaId());

        ShowSchedule saved = showMapper.selectById(show.getShowId());
        return showService.buildShowVO(saved, toZonePriceVOs(dto.getZonePrices(), showPrice));
    }

    // 批量创建场次：按日期范围+每日时段+间隔分钟自动排片，冲突场次跳过并汇总错误，一个都没成功则报错
    @Override
    @Transactional
    public List<ShowVO> batchCreate(ShowBatchCreateDTO dto) {
        assertCinemaScope(dto.getCinemaId());
        // 校验影片存在、未下架、时长有效
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
        // 校验影院、影厅存在且归属
        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影院不存在");
        }
        Hall hall = hallMapper.selectById(dto.getHallId());
        if (hall == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影厅不存在");
        }
        if (!hall.getCinemaId().equals(dto.getCinemaId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "影厅不属于所选影院");
        }
        // 校验时间参数：间隔不小于影片时长、日期范围合法、每日时段合法
        if (dto.getIntervalMin() < durationMin) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "场次间隔（" + dto.getIntervalMin() + " 分钟）不得小于影片时长（"
                            + durationMin + " 分钟）");
        }
        LocalDate dateStart = LocalDate.parse(dto.getDateStart(), DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate dateEnd = LocalDate.parse(dto.getDateEnd(), DateTimeFormatter.ISO_LOCAL_DATE);
        if (dateEnd.isBefore(dateStart)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束日期必须不早于开始日期");
        }
        LocalTime timeStart = LocalTime.parse(dto.getTimeStart(), DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime timeEnd = LocalTime.parse(dto.getTimeEnd(), DateTimeFormatter.ofPattern("HH:mm"));
        if (!timeEnd.isAfter(timeStart)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "每日结束时间必须晚于开始时间");
        }
        // 逐日逐场生成场次：跳过过去场次、冲突场次，成功则落库
        BigDecimal showPrice = computePrice(dto.getZonePrices(), dto.getPrice());
        OffsetDateTime now = OffsetDateTime.now();
        int intervalMin = dto.getIntervalMin();
        List<ShowVO> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (LocalDate date = dateStart; !date.isAfter(dateEnd); date = date.plusDays(1)) {
            LocalTime cursor = timeStart;
            while (true) {
                OffsetDateTime startTime = OffsetDateTime.of(date, cursor, ZoneOffset.ofHours(8));
                OffsetDateTime endTime = startTime.plusMinutes(durationMin);
                // 末场散场不晚于每日结束时间
                if (endTime.toLocalTime().isAfter(timeEnd)) {
                    break;
                }
                // 跳过过去场次
                if (startTime.isBefore(now)) {
                    cursor = cursor.plusMinutes(intervalMin);
                    continue;
                }
                // 同厅冲突检测
                List<ShowSchedule> overlaps = showMapper.findOverlapping(
                        hall.getHallId(), startTime, endTime, null);
                if (overlaps != null && !overlaps.isEmpty()) {
                    String label = date + " " + cursor;
                    errors.add(label + " 与已有场次冲突，已跳过");
                    cursor = cursor.plusMinutes(intervalMin);
                    continue;
                }
                // 落库
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
                showMapper.insert(show);

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
        // 同步ES，全失败则报错
        esIndexService.syncCinema(dto.getCinemaId());
        if (created.isEmpty() && !errors.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "批量创建失败：" + String.join("；", errors));
        }
        return created;
    }

    // 更新场次：仅可修改在售场次，存在有效锁座/订单时拒绝改期，修改后同步ES
    @Override
    @Transactional
    public ShowVO update(String showId, ShowUpdateDTO dto) {
        ShowSchedule show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        if (!"on_sale".equals(show.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅可修改在售场次");
        }
        // 有在途锁座或订单时禁止改期
        int activeLocks = showMapper.countActiveLocksOrOrders(showId);
        if (activeLocks > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "场次存在有效锁座，无法修改");
        }
        // 解析新时间（null则保持原值），校验时间合法性
        OffsetDateTime newStart = dto.getStartTime() != null
                ? OffsetDateTime.parse(dto.getStartTime()) : show.getStartTime();
        OffsetDateTime newEnd = dto.getEndTime() != null
                ? OffsetDateTime.parse(dto.getEndTime()) : show.getEndTime();
        if (!newEnd.isAfter(newStart)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "散场时间必须晚于开场时间");
        }
        // 冲突检测（排除自身）
        List<ShowSchedule> overlaps = showMapper.findOverlapping(
                show.getHallId(), newStart, newEnd, showId);
        if (overlaps != null && !overlaps.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "与场次 " + overlaps.get(0).getShowId() + " 时间冲突");
        }
        show.setStartTime(newStart);
        show.setEndTime(newEnd);
        if (dto.getPrice() != null || dto.getZonePrices() != null) {
            show.setPrice(computePrice(dto.getZonePrices(), dto.getPrice()));
        }
        show.setUpdatedAt(OffsetDateTime.now());
        showMapper.update(show);
        esIndexService.syncCinema(show.getCinemaId());
        ShowSchedule updated = showMapper.selectById(showId);
        return showService.buildShowVO(updated, toZonePriceVOs(dto.getZonePrices(), updated.getPrice()));
    }

    // 取消场次（幂等）：已取消则拒绝重复操作，取消后同步ES
    @Override
    @Transactional
    public ShowVO cancel(String showId) {
        ShowSchedule show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        if ("cancelled".equals(show.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "场次已取消");
        }
        showMapper.cancel(showId);
        esIndexService.syncCinema(show.getCinemaId());
        ShowSchedule updated = showMapper.selectById(showId);
        return showService.buildShowVO(updated, null);
    }

    // 停售：仅可在售场次可停售，停售后同步ES
    @Override
    @Transactional
    public ShowVO closeSale(String showId) {
        ShowSchedule show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        if (!"on_sale".equals(show.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "该场次当前不可停售");
        }
        showMapper.closeSale(showId);
        esIndexService.syncCinema(show.getCinemaId());
        ShowSchedule updated = showMapper.selectById(showId);
        return showService.buildShowVO(updated, null);
    }

    // 恢复售票：仅已停售或已取消可恢复，恢复后同步ES
    @Override
    @Transactional
    public ShowVO resumeSale(String showId) {
        ShowSchedule show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        if (!"off_sale".equals(show.getStatus()) && !"cancelled".equals(show.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅可恢复已停售/取消的场次");
        }
        showMapper.resumeSale(showId);
        esIndexService.syncCinema(show.getCinemaId());
        ShowSchedule updated = showMapper.selectById(showId);
        return showService.buildShowVO(updated, null);
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

    // 分区价格DTO→VO列表：价格保留2位小数
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

    // ── Staff 影院范围校验 ──────────────────────────────────────────

    /** staff 只能操作自己绑定的影院；admin 不受限 */
    private void assertCinemaScope(String cinemaId) {
        if (SecurityContext.isAdmin()) return;
        if (!SecurityContext.hasRole(Roles.STAFF) || !currentStaffCinemaId().equals(cinemaId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该影院");
        }
    }

    // 获取当前staff绑定的影院ID：优先从Token取，Token无则查库
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
