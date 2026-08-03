package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.CinemaCreateDTO;
import com.cinepass.dto.CinemaUpdateDTO;
import com.cinepass.dto.HallCreateDTO;
import com.cinepass.dto.HallUpdateDTO;
import com.cinepass.dto.SeatMapCreateDTO;
import com.cinepass.dto.SeatMapSeatDTO;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.HallMapper;
import com.cinepass.mapper.SeatMapMapper;
import com.cinepass.mapper.SeatMapper;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Hall;
import com.cinepass.model.Seat;
import com.cinepass.model.SeatMap;
import com.cinepass.model.UserAccount;
import com.cinepass.security.Roles;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.CinemaService;
import com.cinepass.util.CinemaIds;
import com.cinepass.vo.CinemaVO;
import com.cinepass.vo.HallVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.SeatMapVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CinemaServiceImpl implements CinemaService {
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final SeatMapMapper seatMapMapper;
    private final SeatMapper seatMapper;
    private final UserAccountMapper userAccountMapper;

    public CinemaServiceImpl(CinemaMapper cinemaMapper, HallMapper hallMapper, SeatMapMapper seatMapMapper,
                             SeatMapper seatMapper, UserAccountMapper userAccountMapper) {
        this.cinemaMapper = cinemaMapper;
        this.hallMapper = hallMapper;
        this.seatMapMapper = seatMapMapper;
        this.seatMapper = seatMapper;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public PageResult<CinemaVO> listCinemas(String movieId, BigDecimal lat, BigDecimal lng,
                                             Integer radiusMeters, String sort, int page, int size) {
        if (!"distance".equals(sort) && !"price".equals(sort)) {
            throw new BusinessException(ResultCode.FAIL, "sort 仅支持 distance 或 price");
        }
        if ("distance".equals(sort) && (lat == null || lng == null)) {
            throw new BusinessException(ResultCode.FAIL, "distance requires lat and lng");
        }
        if ((lat == null) != (lng == null)) {
            throw new BusinessException(ResultCode.FAIL, "lat and lng must be paired");
        }
        int actualPage = normalizePage(page);
        int actualSize = normalizeSize(size);
        int radius = radiusMeters == null ? 5000 : radiusMeters;
        if (radius < 1 || radius > 50000) {
            throw new BusinessException(ResultCode.FAIL, "radiusMeters 必须在 1 到 50000 之间");
        }
        long total = cinemaMapper.countNearby(movieId, lat, lng, radius);
        List<Cinema> rows = cinemaMapper.selectNearby(movieId, lat, lng, radius, sort,
                (actualPage - 1) * actualSize, actualSize);
        List<CinemaVO> items = new ArrayList<CinemaVO>();
        if (rows != null) {
            for (Cinema row : rows) {
                items.add(toCinemaVO(row, false));
            }
        }
        return new PageResult<CinemaVO>(items, actualPage, actualSize, total);
    }

    @Override
    public CinemaVO getCinema(String cinemaId) {
        Cinema cinema = requireCinema(cinemaId);
        CinemaVO result = toCinemaVO(cinema, true);
        List<Hall> halls = hallMapper.selectByCinemaId(cinemaId);
        List<HallVO> hallVos = new ArrayList<HallVO>();
        if (halls != null) {
            for (Hall hall : halls) {
                hallVos.add(toHallVO(hall));
            }
        }
        result.setHalls(hallVos);
        return result;
    }

    @Override
    @Transactional
    public CinemaVO createCinema(CinemaCreateDTO dto) {
        Cinema cinema = new Cinema();
        cinema.setCinemaId(StringUtils.hasText(dto.getCinemaId()) ? dto.getCinemaId().trim() : CinemaIds.nextCinemaId());
        if (cinemaMapper.exists(cinema.getCinemaId())) {
            throw new BusinessException(ResultCode.CONFLICT, "cinema already exists");
        }
        cinema.setCityId(StringUtils.hasText(dto.getCityId()) ? dto.getCityId().trim() : "city_sh");
        cinema.setName(dto.getName().trim());
        cinema.setAddress(dto.getAddress().trim());
        cinema.setLat(dto.getLat());
        cinema.setLng(dto.getLng());
        cinema.setTrafficNote(trimToNull(dto.getTrafficNote()));
        cinema.setTagsJson(toTagsJson(dto.getTags()));
        cinema.setCreatedAt(OffsetDateTime.now());
        cinema.setUpdatedAt(OffsetDateTime.now());
        cinemaMapper.insert(cinema);
        return toCinemaVO(cinema, true);
    }

    @Override
    @Transactional
    public CinemaVO updateCinema(String cinemaId, CinemaUpdateDTO dto) {
        assertCinemaScope(cinemaId);
        Cinema cinema = requireCinema(cinemaId);
        if (dto.getCityId() != null) cinema.setCityId(dto.getCityId().trim());
        if (dto.getName() != null) cinema.setName(dto.getName().trim());
        if (dto.getAddress() != null) cinema.setAddress(dto.getAddress().trim());
        if (dto.getLat() != null) cinema.setLat(dto.getLat());
        if (dto.getLng() != null) cinema.setLng(dto.getLng());
        if (dto.getTrafficNote() != null) cinema.setTrafficNote(trimToNull(dto.getTrafficNote()));
        if (dto.getTags() != null) cinema.setTagsJson(toTagsJson(dto.getTags()));
        cinema.setUpdatedAt(OffsetDateTime.now());
        cinemaMapper.update(cinema);
        return toCinemaVO(cinema, true);
    }

    @Override
    @Transactional
    public SeatMapVO createSeatMap(SeatMapCreateDTO dto) {
        assertCinemaScope(dto.getCinemaId());
        requireCinema(dto.getCinemaId());
        String seatMapId = StringUtils.hasText(dto.getSeatMapId()) ? dto.getSeatMapId() : CinemaIds.nextSeatMapId();
        if (seatMapMapper.selectById(seatMapId) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "座位图已存在");
        }
        List<Seat> seats = toSeats(seatMapId, dto);
        SeatMap seatMap = new SeatMap();
        seatMap.setSeatMapId(seatMapId);
        seatMap.setCinemaId(dto.getCinemaId());
        seatMap.setRowsN(dto.getRows());
        seatMap.setColsN(dto.getCols());
        seatMap.setScreenLabel(StringUtils.hasText(dto.getScreenLabel()) ? dto.getScreenLabel().trim() : "银幕");
        seatMap.setMutable(true);
        seatMap.setSeatCount(seats.size());
        seatMapMapper.insert(seatMap);
        seatMapper.insertBatch(seats);
        return toSeatMapVO(seatMap);
    }

    @Override
    @Transactional
    public HallVO createHall(HallCreateDTO dto) {
        assertCinemaScope(dto.getCinemaId());
        requireCinema(dto.getCinemaId());
        SeatMap seatMap = seatMapMapper.selectById(dto.getSeatMapId());
        if (seatMap == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "座位图不存在");
        }
        if (!dto.getCinemaId().equals(seatMap.getCinemaId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "座位图不属于该影院");
        }
        String hallId = StringUtils.hasText(dto.getHallId()) ? dto.getHallId() : CinemaIds.nextHallId();
        if (hallMapper.selectById(hallId) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "影厅已存在");
        }
        Hall hall = new Hall();
        hall.setHallId(hallId);
        hall.setCinemaId(dto.getCinemaId());
        hall.setName(dto.getName().trim());
        hall.setSeatMapId(dto.getSeatMapId());
        hallMapper.insert(hall);
        return toHallVO(hall);
    }

    @Override
    public PageResult<HallVO> listAdminHalls(String cinemaId, int page, int size) {
        String actualCinemaId = resolveAdminCinemaId(cinemaId);
        int actualPage = normalizePage(page);
        int actualSize = normalizeSize(size);
        long total = hallMapper.countByCinemaId(actualCinemaId);
        List<Hall> halls = hallMapper.selectAdminByCinemaId(actualCinemaId,
                (actualPage - 1) * actualSize, actualSize);
        List<HallVO> items = new ArrayList<HallVO>();
        if (halls != null) for (Hall hall : halls) items.add(toHallVO(hall));
        return new PageResult<HallVO>(items, actualPage, actualSize, total);
    }

    @Override
    @Transactional
    public HallVO updateHall(String hallId, HallUpdateDTO dto) {
        Hall hall = hallMapper.selectById(hallId);
        if (hall == null) throw new BusinessException(ResultCode.NOT_FOUND, "影厅不存在");
        assertCinemaScope(hall.getCinemaId());
        hallMapper.updateName(hallId, dto.getName().trim());
        hall.setName(dto.getName().trim());
        return toHallVO(hall);
    }

    private String resolveAdminCinemaId(String cinemaId) {
        if (SecurityContext.isAdmin()) {
            if (!StringUtils.hasText(cinemaId)) throw new BusinessException(ResultCode.FAIL, "管理员查询影厅时必须提供 cinemaId");
            return cinemaId;
        }
        String staffCinemaId = currentStaffCinemaId();
        if (StringUtils.hasText(cinemaId) && !staffCinemaId.equals(cinemaId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他影院");
        }
        return staffCinemaId;
    }

    private void assertCinemaScope(String cinemaId) {
        if (SecurityContext.isAdmin()) return;
        if (!SecurityContext.hasRole(Roles.STAFF) || !currentStaffCinemaId().equals(cinemaId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该影院");
        }
    }

    private String currentStaffCinemaId() {
        String cinemaIdFromToken = SecurityContext.getCurrentCinemaId();
        if (StringUtils.hasText(cinemaIdFromToken)) {
            return cinemaIdFromToken;
        }
        UserAccount user = userAccountMapper.findById(SecurityContext.getCurrentUserId());
        if (user == null || !StringUtils.hasText(user.getCinemaId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "工作人员未绑定影院");
        }
        return user.getCinemaId();
    }

    private Cinema requireCinema(String cinemaId) {
        Cinema cinema = cinemaMapper.selectById(cinemaId);
        if (cinema == null) throw new BusinessException(ResultCode.NOT_FOUND, "影院不存在");
        return cinema;
    }

    private List<Seat> toSeats(String seatMapId, SeatMapCreateDTO dto) {
        Set<String> coordinates = new HashSet<String>();
        List<Seat> result = new ArrayList<Seat>();
        int index = 0;
        for (SeatMapSeatDTO source : dto.getSeats()) {
            if (source.getRowNo() > dto.getRows() || source.getColNo() > dto.getCols()) {
                throw new BusinessException(ResultCode.FAIL, "座位坐标超出座位图范围");
            }
            String coordinate = source.getRowNo() + ":" + source.getColNo();
            if (!coordinates.add(coordinate)) throw new BusinessException(ResultCode.CONFLICT, "座位坐标重复");
            Seat seat = new Seat();
            seat.setSeatId(seatMapId + ":" + source.getRowNo() + ":" + source.getColNo());
            seat.setSeatMapId(seatMapId);
            seat.setGraphRow(source.getRowNo());
            seat.setGraphCol(source.getColNo());
            seat.setRowNo(source.getRowNo());
            seat.setColNo(source.getColNo());
            seat.setSeatName(source.getSeatName().trim());
            seat.setSeatType(source.getType().trim());
            seat.setZone(source.getZone().trim());
            seat.setCouplePairId(trimToNull(source.getCouplePairId()));
            seat.setDefaultStatus("available");
            result.add(seat);
            index++;
        }
        return result;
    }

    private CinemaVO toCinemaVO(Cinema cinema, boolean detail) {
        List<String> tags = StringUtils.hasText(cinema.getTagsJson())
                ? JSON.parseArray(cinema.getTagsJson(), String.class) : Collections.<String>emptyList();
        return CinemaVO.builder().cinemaId(cinema.getCinemaId()).cityId(cinema.getCityId())
                .name(cinema.getName()).address(cinema.getAddress()).distanceMeters(cinema.getDistanceMeters())
                .minPrice(cinema.getMinPrice()).trafficNote(detail ? cinema.getTrafficNote() : null)
                .tags(detail ? tags : null).build();
    }

    private HallVO toHallVO(Hall hall) {
        return HallVO.builder().hallId(hall.getHallId()).cinemaId(hall.getCinemaId()).name(hall.getName())
                .seatMapId(hall.getSeatMapId()).showCount(hall.getShowCount()).build();
    }

    private SeatMapVO toSeatMapVO(SeatMap seatMap) {
        return SeatMapVO.builder().seatMapId(seatMap.getSeatMapId()).cinemaId(seatMap.getCinemaId())
                .rows(seatMap.getRowsN()).cols(seatMap.getColsN()).screenLabel(seatMap.getScreenLabel())
                .mutable(seatMap.getMutable()).seatCount(seatMap.getSeatCount()).build();
    }

    private String toTagsJson(List<String> tags) { return JSON.toJSONString(tags == null ? Collections.emptyList() : tags); }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private int normalizePage(int page) { return page < 1 ? 1 : page; }
    private int normalizeSize(int size) { return size < 1 ? 20 : Math.min(size, 50); }
}
