package com.cinepass.service.impl;

import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.ShowCreateDTO;
import com.cinepass.dto.ShowUpdateDTO;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.HallMapper;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.SeatMapMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Hall;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
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
import java.time.OffsetDateTime;
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

    public AdminShowServiceImpl(ShowMapper showMapper,
                                MovieMapper movieMapper,
                                CinemaMapper cinemaMapper,
                                HallMapper hallMapper,
                                SeatMapMapper seatMapMapper,
                                ShowService showService,
                                SeatInventoryService seatInventoryService,
                                EsIndexService esIndexService) {
        this.showMapper = showMapper;
        this.movieMapper = movieMapper;
        this.cinemaMapper = cinemaMapper;
        this.hallMapper = hallMapper;
        this.seatMapMapper = seatMapMapper;
        this.showService = showService;
        this.seatInventoryService = seatInventoryService;
        this.esIndexService = esIndexService;
    }

    @Override
    @Transactional
    public ShowVO create(ShowCreateDTO dto) {
        Movie movie = movieMapper.selectById(dto.getMovieId());
        if (movie == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
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
        OffsetDateTime startTime = OffsetDateTime.parse(dto.getStartTime());
        OffsetDateTime endTime = OffsetDateTime.parse(dto.getEndTime());
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "散场时间必须晚于开场时间");
        }
        if (startTime.isBefore(OffsetDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不允许创建过去时间的场次");
        }
        List<ShowSchedule> overlaps = showMapper.findOverlapping(
                hall.getHallId(), startTime, endTime, null);
        if (overlaps != null && !overlaps.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "与场次 " + overlaps.get(0).getShowId() + " 时间冲突，需预留"
                            + SHOW_BUFFER_MINUTES + " 分钟缓冲");
        }

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
        // 排片后座位图不可再改布局，避免库存与模板错位
        if (StringUtils.hasText(show.getSeatMapId())) {
            seatMapMapper.markImmutable(show.getSeatMapId());
        }
        // 排片后立即播种 seat_status，避免购票侧读到空库存
        seatInventoryService.ensureSeatStatus(show.getShowId(), show.getSeatMapId());
        esIndexService.syncCinema(show.getCinemaId());

        ShowSchedule saved = showMapper.selectById(show.getShowId());
        return showService.buildShowVO(saved, toZonePriceVOs(dto.getZonePrices(), showPrice));
    }

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
        // 有在途锁座或订单时禁止改期，避免已售座位时间错位
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

    @Override
    @Transactional
    public ShowVO resumeSale(String showId) {
        ShowSchedule show = showMapper.selectById(showId);
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        if (!"cancelled".equals(show.getStatus())) {
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
}
