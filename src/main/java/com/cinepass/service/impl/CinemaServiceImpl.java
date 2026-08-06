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
import com.cinepass.dto.SeatMapUpdateDTO;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.HallMapper;
import com.cinepass.mapper.SeatMapMapper;
import com.cinepass.mapper.SeatMapper;
import com.cinepass.mapper.ShowMapper;
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
import com.cinepass.vo.SeatMapDeletedVO;
import com.cinepass.vo.SeatMapVO;
import com.cinepass.vo.SeatMapSeatVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * {@link CinemaService} 实现。
 * <p>数据范围：admin 全量；staff 仅绑定影院。座位图按画布坐标稀疏落库，业务排座号可自动编号。
 */
@Service
public class CinemaServiceImpl implements CinemaService {
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final SeatMapMapper seatMapMapper;
    private final SeatMapper seatMapper;
    private final ShowMapper showMapper;
    private final UserAccountMapper userAccountMapper;

    public CinemaServiceImpl(CinemaMapper cinemaMapper, HallMapper hallMapper, SeatMapMapper seatMapMapper,
                             SeatMapper seatMapper, ShowMapper showMapper, UserAccountMapper userAccountMapper) {
        this.cinemaMapper = cinemaMapper;
        this.hallMapper = hallMapper;
        this.seatMapMapper = seatMapMapper;
        this.seatMapper = seatMapper;
        this.showMapper = showMapper;
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
        cinema.setCityName(dto.getCityName().trim());
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
        if (dto.getCityName() != null) cinema.setCityName(dto.getCityName().trim());
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
    public void deleteCinema(String cinemaId) {
        requireCinema(cinemaId);
        if (userAccountMapper.countStaffByCinemaId(cinemaId) > 0) {
            throw new BusinessException(ResultCode.FAIL, "影院仍绑定员工，请先迁移员工");
        }
        if (cinemaMapper.softDelete(cinemaId) != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影院不存在");
        }
    }

    @Override
    @Transactional
    public SeatMapVO createSeatMap(SeatMapCreateDTO dto) {
        String cinemaId = resolveWriteCinemaId(dto.getCinemaId());
        requireCinema(cinemaId);
        String seatMapId = StringUtils.hasText(dto.getSeatMapId()) ? dto.getSeatMapId() : CinemaIds.nextSeatMapId();
        if (seatMapMapper.selectById(seatMapId) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "座位图已存在");
        }
        List<Seat> seats = toSeats(seatMapId, dto);
        SeatMap seatMap = new SeatMap();
        seatMap.setSeatMapId(seatMapId);
        String screenLabel = StringUtils.hasText(dto.getScreenLabel()) ? dto.getScreenLabel().trim() : "银幕";
        seatMap.setName(StringUtils.hasText(dto.getName()) ? dto.getName().trim() : screenLabel);
        seatMap.setCinemaId(cinemaId);
        seatMap.setRowsN(dto.getRows());
        seatMap.setColsN(dto.getCols());
        seatMap.setScreenLabel(screenLabel);
        seatMap.setMutable(true);
        seatMap.setSeatCount(seats.size());
        seatMapMapper.insert(seatMap);
        seatMapper.insertBatch(seats);
        return toSeatMapVO(seatMap, seats);
    }

    @Override
    public PageResult<SeatMapVO> listSeatMaps(String cinemaId, int page, int size) {
        String actualCinemaId = resolveListCinemaId(cinemaId);
        int actualPage = normalizePage(page);
        int actualSize = normalizeSize(size);
        long total = seatMapMapper.countByCinemaId(actualCinemaId);
        List<SeatMap> rows = seatMapMapper.selectByCinemaId(actualCinemaId,
                (actualPage - 1) * actualSize, actualSize);
        List<SeatMapVO> items = new ArrayList<SeatMapVO>();
        if (rows != null) {
            for (SeatMap seatMap : rows) {
                // 列表不携带座位明细，减轻体积
                items.add(toSeatMapVO(syncMutableWithShows(seatMap), Collections.<Seat>emptyList()));
            }
        }
        return new PageResult<SeatMapVO>(items, actualPage, actualSize, total);
    }

    @Override
    public SeatMapVO getSeatMap(String seatMapId) {
        SeatMap seatMap = requireSeatMap(seatMapId);
        assertCinemaScope(seatMap.getCinemaId());
        List<Seat> seats = seatMapper.selectBySeatMapId(seatMapId);
        return toSeatMapVO(syncMutableWithShows(seatMap), seats != null ? seats : Collections.<Seat>emptyList());
    }

    @Override
    @Transactional
    public SeatMapVO updateSeatMap(String seatMapId, SeatMapUpdateDTO dto) {
        SeatMap seatMap = requireSeatMap(seatMapId);
        assertCinemaScope(seatMap.getCinemaId());
        // 已排片（有效场次）或已标记不可变则禁止改座位集合，避免库存错位
        if (Boolean.FALSE.equals(seatMap.getMutable()) || showMapper.countActiveBySeatMapId(seatMapId) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "座位图已被场次引用，不可修改");
        }
        SeatMapCreateDTO createShape = new SeatMapCreateDTO();
        createShape.setRows(dto.getRows());
        createShape.setCols(dto.getCols());
        createShape.setScreenLabel(dto.getScreenLabel());
        createShape.setSeats(dto.getSeats());
        List<Seat> seats = toSeats(seatMapId, createShape);
        seatMap.setRowsN(dto.getRows());
        seatMap.setColsN(dto.getCols());
        seatMap.setScreenLabel(StringUtils.hasText(dto.getScreenLabel()) ? dto.getScreenLabel().trim() : "银幕");
        seatMap.setSeatCount(seats.size());
        if (seatMapMapper.update(seatMap) != 1) {
            throw new BusinessException(ResultCode.CONFLICT, "座位图不可修改");
        }
        seatMapper.deleteBySeatMapId(seatMapId);
        seatMapper.insertBatch(seats);
        return toSeatMapVO(seatMap, seats);
    }

    @Override
    @Transactional
    public SeatMapDeletedVO deleteSeatMap(String seatMapId) {
        SeatMap seatMap = requireSeatMap(seatMapId);
        assertCinemaScope(seatMap.getCinemaId());
        if (hallMapper.countBySeatMapId(seatMapId) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "座位图仍被影厅引用，无法删除");
        }
        if (showMapper.countActiveBySeatMapId(seatMapId) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "座位图仍被有效场次引用，无法删除");
        }
        seatMapper.deleteBySeatMapId(seatMapId);
        if (seatMapMapper.deleteById(seatMapId) != 1) {
            throw new BusinessException(ResultCode.CONFLICT, "座位图不可删除");
        }
        return SeatMapDeletedVO.builder().deleted(true).seatMapId(seatMapId).build();
    }

    @Override
    @Transactional
    public HallVO createHall(HallCreateDTO dto) {
        String cinemaId = resolveWriteCinemaId(dto.getCinemaId());
        requireCinema(cinemaId);
        SeatMap seatMap = seatMapMapper.selectById(dto.getSeatMapId());
        if (seatMap == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "座位图不存在");
        }
        if (!cinemaId.equals(seatMap.getCinemaId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "座位图不属于该影院");
        }
        String hallId = StringUtils.hasText(dto.getHallId()) ? dto.getHallId() : CinemaIds.nextHallId();
        if (hallMapper.selectById(hallId) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "影厅已存在");
        }
        Hall hall = new Hall();
        hall.setHallId(hallId);
        hall.setCinemaId(cinemaId);
        hall.setName(dto.getName().trim());
        hall.setSeatMapId(dto.getSeatMapId());
        hallMapper.insert(hall);
        return toHallVO(hall);
    }

    @Override
    public PageResult<HallVO> listAdminHalls(String cinemaId, int page, int size) {
        String actualCinemaId = resolveAdminCinemaId(cinemaId);
        requireCinema(actualCinemaId);
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
        requireCinema(hall.getCinemaId());
        assertCinemaScope(hall.getCinemaId());
        hallMapper.updateName(hallId, dto.getName().trim());
        hall.setName(dto.getName().trim());
        return toHallVO(hall);
    }

    /** admin 必须显式传院；staff 强制本院，传其他院则 403 */
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

    /** 非 admin 时写操作必须落在绑定影院 */
    private void assertCinemaScope(String cinemaId) {
        if (SecurityContext.isAdmin()) return;
        if (!SecurityContext.hasRole(Roles.STAFF) || !currentStaffCinemaId().equals(cinemaId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该影院");
        }
    }

    /** 优先 JWT cinemaId；缺失时回查账号绑定 */
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

    /** 创建座位图/影厅时解析目标影院 ID */
    private String resolveWriteCinemaId(String requestedCinemaId) {
        if (SecurityContext.isAdmin()) {
            if (!StringUtils.hasText(requestedCinemaId)) {
                throw new BusinessException(ResultCode.FAIL, "管理员创建资源时必须提供 cinemaId");
            }
            return requestedCinemaId.trim();
        }
        String staffCinemaId = currentStaffCinemaId();
        if (StringUtils.hasText(requestedCinemaId) && !staffCinemaId.equals(requestedCinemaId.trim())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他影院");
        }
        return staffCinemaId;
    }

    /** admin 列表可按 cinemaId 筛；staff 强制本院 */
    private String resolveListCinemaId(String cinemaId) {
        if (SecurityContext.isAdmin()) {
            if (!StringUtils.hasText(cinemaId)) {
                throw new BusinessException(ResultCode.FAIL, "管理员查询座位图时必须提供 cinemaId");
            }
            requireCinema(cinemaId.trim());
            return cinemaId.trim();
        }
        String staffCinemaId = currentStaffCinemaId();
        if (StringUtils.hasText(cinemaId) && !staffCinemaId.equals(cinemaId.trim())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他影院");
        }
        return staffCinemaId;
    }

    private SeatMap requireSeatMap(String seatMapId) {
        SeatMap seatMap = seatMapMapper.selectById(seatMapId);
        if (seatMap == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "座位图不存在");
        }
        return seatMap;
    }

    /**
     * 有效场次引用优先于库内 mutable 标记：历史数据可能未及时 markImmutable，读路径补齐并回写。
     * cancelled 的历史场次不计入。
     */
    private SeatMap syncMutableWithShows(SeatMap seatMap) {
        if (seatMap == null) {
            return null;
        }
        if (!Boolean.FALSE.equals(seatMap.getMutable())
                && showMapper.countActiveBySeatMapId(seatMap.getSeatMapId()) > 0) {
            seatMapMapper.markImmutable(seatMap.getSeatMapId());
            seatMap.setMutable(false);
        }
        return seatMap;
    }

    private Cinema requireCinema(String cinemaId) {
        Cinema cinema = cinemaMapper.selectById(cinemaId);
        if (cinema == null) throw new BusinessException(ResultCode.NOT_FOUND, "影院不存在");
        return cinema;
    }

    private List<Seat> toSeats(String seatMapId, SeatMapCreateDTO dto) {
        if (dto.getRows() != null && dto.getCols() != null) {
            return buildSeats(seatMapId, dto);
        }
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

    /**
     * 按画布坐标建座：空行跳过不占业务排号；未传 rowNo/colNo 时同行从左到右编号。
     */
    private List<Seat> buildSeats(String seatMapId, SeatMapCreateDTO dto) {
        Set<String> graphCoordinates = new HashSet<String>();
        Map<Integer, List<SeatMapSeatDTO>> seatsByGraphRow = new HashMap<Integer, List<SeatMapSeatDTO>>();
        for (SeatMapSeatDTO source : dto.getSeats()) {
            if (source.getGraphRow() > dto.getRows() || source.getGraphCol() > dto.getCols()) {
                throw new BusinessException(ResultCode.FAIL, "座位画布坐标越界");
            }
            String graphCoordinate = source.getGraphRow() + ":" + source.getGraphCol();
            if (!graphCoordinates.add(graphCoordinate)) {
                throw new BusinessException(ResultCode.CONFLICT, "座位画布坐标重复");
            }
            List<SeatMapSeatDTO> rowSeats = seatsByGraphRow.get(source.getGraphRow());
            if (rowSeats == null) {
                rowSeats = new ArrayList<SeatMapSeatDTO>();
                seatsByGraphRow.put(source.getGraphRow(), rowSeats);
            }
            rowSeats.add(source);
        }

        List<Seat> result = new ArrayList<Seat>();
        Set<String> businessCoordinates = new HashSet<String>();
        Set<String> seatIds = new HashSet<String>();
        Set<String> seatNames = new HashSet<String>();
        int businessRow = 0;
        for (int graphRow = 1; graphRow <= dto.getRows(); graphRow++) {
            List<SeatMapSeatDTO> rowSeats = seatsByGraphRow.get(graphRow);
            if (rowSeats == null || rowSeats.isEmpty()) continue;
            businessRow++;
            Collections.sort(rowSeats, new java.util.Comparator<SeatMapSeatDTO>() {
                @Override
                public int compare(SeatMapSeatDTO left, SeatMapSeatDTO right) {
                    return left.getGraphCol().compareTo(right.getGraphCol());
                }
            });
            for (int index = 0; index < rowSeats.size(); index++) {
                SeatMapSeatDTO source = rowSeats.get(index);
                int rowNo = source.getRowNo() == null ? businessRow : source.getRowNo();
                int colNo = source.getColNo() == null ? index + 1 : source.getColNo();
                String businessCoordinate = rowNo + ":" + colNo;
                if (!businessCoordinates.add(businessCoordinate)) {
                    throw new BusinessException(ResultCode.CONFLICT, "业务座位号重复");
                }
                String seatId = StringUtils.hasText(source.getSeatId()) ? source.getSeatId().trim()
                        : seatMapId + ":" + source.getGraphRow() + ":" + source.getGraphCol();
                if (!seatIds.add(seatId)) throw new BusinessException(ResultCode.CONFLICT, "座位 ID 重复");
                String seatName = rowNo + "排" + colNo + "座";
                if (!seatNames.add(seatName)) throw new BusinessException(ResultCode.CONFLICT, "座位名称重复");
                String type = StringUtils.hasText(source.getType()) ? source.getType().trim() : "normal";
                if (!"normal".equals(type) && !"couple".equals(type) && !"disabled".equals(type)) {
                    throw new BusinessException(ResultCode.FAIL, "座位类型不合法");
                }
                String zone = StringUtils.hasText(source.getZone()) ? source.getZone().trim() : "normal";
                String defaultStatus = StringUtils.hasText(source.getDefaultStatus())
                        ? source.getDefaultStatus().trim() : "available";
                if (!"available".equals(defaultStatus) && !"unavailable".equals(defaultStatus)) {
                    throw new BusinessException(ResultCode.FAIL, "座位默认状态不合法");
                }
                if ("couple".equals(type) && !StringUtils.hasText(source.getCouplePairId())) {
                    throw new BusinessException(ResultCode.FAIL, "情侣座必须提供 couplePairId");
                }
                Seat seat = new Seat();
                seat.setSeatId(seatId);
                seat.setSeatMapId(seatMapId);
                seat.setGraphRow(source.getGraphRow());
                seat.setGraphCol(source.getGraphCol());
                seat.setRowNo(rowNo);
                seat.setColNo(colNo);
                seat.setSeatName(seatName);
                seat.setSeatType(type);
                seat.setZone(zone);
                seat.setCouplePairId(trimToNull(source.getCouplePairId()));
                seat.setDefaultStatus(defaultStatus);
                result.add(seat);
            }
        }
        validateCoupleSeats(result);
        return result;
    }

    /** 同一 couplePairId 必须恰好两座 */
    private void validateCoupleSeats(List<Seat> seats) {
        Map<String, Integer> pairCounts = new HashMap<String, Integer>();
        for (Seat seat : seats) {
            if (!"couple".equals(seat.getSeatType())) continue;
            Integer count = pairCounts.get(seat.getCouplePairId());
            pairCounts.put(seat.getCouplePairId(), count == null ? 1 : count + 1);
        }
        for (Integer count : pairCounts.values()) {
            if (count != 2) throw new BusinessException(ResultCode.FAIL, "情侣座必须成对创建");
        }
    }

    /** detail=false 时省略 trafficNote/tags，减轻附近列表体积 */
    private CinemaVO toCinemaVO(Cinema cinema, boolean detail) {
        List<String> tags = StringUtils.hasText(cinema.getTagsJson())
                ? JSON.parseArray(cinema.getTagsJson(), String.class) : Collections.<String>emptyList();
        return CinemaVO.builder().cinemaId(cinema.getCinemaId()).cityId(cinema.getCityId()).cityName(cinema.getCityName())
                .name(cinema.getName()).address(cinema.getAddress()).distanceMeters(cinema.getDistanceMeters())
                .minPrice(cinema.getMinPrice()).trafficNote(detail ? cinema.getTrafficNote() : null)
                .tags(detail ? tags : null).build();
    }

    private HallVO toHallVO(Hall hall) {
        return HallVO.builder().hallId(hall.getHallId()).cinemaId(hall.getCinemaId()).name(hall.getName())
                .seatMapId(hall.getSeatMapId()).showCount(hall.getShowCount()).build();
    }

    private SeatMapVO toSeatMapVO(SeatMap seatMap, List<Seat> seats) {
        List<SeatMapSeatVO> seatVos = new ArrayList<SeatMapSeatVO>();
        for (Seat seat : seats) {
            seatVos.add(SeatMapSeatVO.builder().seatId(seat.getSeatId()).seatName(seat.getSeatName())
                    .rowNo(seat.getRowNo()).colNo(seat.getColNo()).graphRow(seat.getGraphRow())
                    .graphCol(seat.getGraphCol()).type(seat.getSeatType()).zone(seat.getZone())
                    .defaultStatus(seat.getDefaultStatus()).couplePairId(seat.getCouplePairId()).build());
        }
        return SeatMapVO.builder().seatMapId(seatMap.getSeatMapId()).name(seatMap.getName())
                .cinemaId(seatMap.getCinemaId())
                .rows(seatMap.getRowsN()).cols(seatMap.getColsN()).screenLabel(seatMap.getScreenLabel())
                .mutable(seatMap.getMutable()).seatCount(seatMap.getSeatCount()).seats(seatVos).build();
    }

    private String toTagsJson(List<String> tags) { return JSON.toJSONString(tags == null ? Collections.emptyList() : tags); }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private int normalizePage(int page) { return page < 1 ? 1 : page; }
    private int normalizeSize(int size) { return size < 1 ? 20 : Math.min(size, 50); }
}
