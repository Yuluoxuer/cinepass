package com.cinepass.service.impl;

import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.mapper.SeatMapMapper;
import com.cinepass.mapper.SeatMapper;
import com.cinepass.mapper.SeatStatusMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.Seat;
import com.cinepass.model.SeatMap;
import com.cinepass.model.SeatStatus;
import com.cinepass.model.ShowSchedule;
import com.cinepass.service.SeatInventoryService;
import com.cinepass.vo.SeatVO;
import com.cinepass.vo.ShowSeatMapVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SeatInventoryService} 实现。
 */
@Service
public class SeatInventoryServiceImpl implements SeatInventoryService {

    private final ShowMapper showMapper;
    private final SeatMapMapper seatMapMapper;
    private final SeatMapper seatMapper;
    private final SeatStatusMapper seatStatusMapper;

    public SeatInventoryServiceImpl(ShowMapper showMapper,
                                    SeatMapMapper seatMapMapper,
                                    SeatMapper seatMapper,
                                    SeatStatusMapper seatStatusMapper) {
        this.showMapper = showMapper;
        this.seatMapMapper = seatMapMapper;
        this.seatMapper = seatMapper;
        this.seatStatusMapper = seatStatusMapper;
    }

    @Override
    @Transactional
    public void ensureSeatStatus(String showId, String seatMapId) {
        if (!StringUtils.hasText(showId) || !StringUtils.hasText(seatMapId)) {
            return;
        }
        int total = seatStatusMapper.countTotal(showId);
        if (total > 0) {
            return;
        }
        List<Seat> seats = seatMapper.selectBySeatMapId(seatMapId);
        if (seats == null || seats.isEmpty()) {
            return;
        }
        List<SeatStatus> rows = new ArrayList<SeatStatus>();
        for (Seat seat : seats) {
            SeatStatus ss = new SeatStatus();
            ss.setShowId(showId);
            ss.setSeatId(seat.getSeatId());
            // default_status=unavailable → 不可选；其余视为可售
            if ("unavailable".equals(seat.getDefaultStatus())) {
                ss.setStatus("unavailable");
            } else {
                ss.setStatus("available");
            }
            rows.add(ss);
        }
        seatStatusMapper.batchInsert(rows);
    }

    @Override
    @Transactional
    public ShowSeatMapVO getShowSeatMap(String showId) {
        if (!StringUtils.hasText(showId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "showId 不能为空");
        }
        ShowSchedule show = showMapper.selectById(showId.trim());
        if (show == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场次不存在");
        }
        ensureSeatStatus(show.getShowId(), show.getSeatMapId());
        // 读图前释放已过期锁，避免 UI 永久显示 locked
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(8));
        seatStatusMapper.releaseExpiredByShow(show.getShowId(), now);

        SeatMap seatMap = seatMapMapper.selectById(show.getSeatMapId());
        List<Seat> seats = seatMapper.selectBySeatMapId(show.getSeatMapId());
        List<SeatStatus> statuses = seatStatusMapper.selectByShowId(show.getShowId());
        Map<String, SeatStatus> statusMap = new HashMap<String, SeatStatus>();
        if (statuses != null) {
            for (SeatStatus ss : statuses) {
                statusMap.put(ss.getSeatId(), ss);
            }
        }

        List<SeatVO> seatVos = new ArrayList<SeatVO>();
        if (seats != null) {
            for (Seat seat : seats) {
                SeatStatus ss = statusMap.get(seat.getSeatId());
                String status = ss != null ? ss.getStatus() : "available";
                if ("unavailable".equals(seat.getDefaultStatus()) && ss == null) {
                    status = "unavailable";
                }
                if ("locked".equals(status) && ss != null && ss.getExpireAt() != null
                        && !ss.getExpireAt().isAfter(now)) {
                    status = "available";
                }
                seatVos.add(SeatVO.builder()
                        .seatId(seat.getSeatId())
                        .seatName(seat.getSeatName())
                        .rowNo(seat.getRowNo())
                        .colNo(seat.getColNo())
                        .graphRow(seat.getGraphRow())
                        .graphCol(seat.getGraphCol())
                        .type(seat.getSeatType())
                        .zone(seat.getZone())
                        .status(status)
                        .couplePairId(seat.getCouplePairId())
                        .build());
            }
        }

        Map<String, String> legend = new LinkedHashMap<String, String>();
        legend.put("available", "可选");
        legend.put("locked", "锁定中");
        legend.put("sold", "已售");
        legend.put("unavailable", "不可选");

        return ShowSeatMapVO.builder()
                .showId(show.getShowId())
                .seatMapId(show.getSeatMapId())
                .rows(seatMap != null ? seatMap.getRowsN() : null)
                .cols(seatMap != null ? seatMap.getColsN() : null)
                .screenLabel(seatMap != null && StringUtils.hasText(seatMap.getScreenLabel())
                        ? seatMap.getScreenLabel() : "银幕")
                .price(show.getPrice())
                .legend(Collections.unmodifiableMap(legend))
                .seats(seatVos)
                .build();
    }
}
