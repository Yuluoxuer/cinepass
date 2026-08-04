package com.cinepass.service;

import com.cinepass.dto.CinemaCreateDTO;
import com.cinepass.dto.CinemaUpdateDTO;
import com.cinepass.dto.HallCreateDTO;
import com.cinepass.dto.HallUpdateDTO;
import com.cinepass.dto.SeatMapCreateDTO;
import com.cinepass.vo.CinemaVO;
import com.cinepass.vo.HallVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.SeatMapVO;

import java.math.BigDecimal;

/**
 * 影院 / 影厅 / 座位图业务。
 * <p>staff 写操作自动收窄到绑定 {@code cinemaId}；admin 须显式传影院 ID。
 */
public interface CinemaService {

    /**
     * 附近影院分页。
     * {@code sort} 仅 distance / price；distance 时 lat/lng 必填；radius 默认 5000，上限 50000。
     */
    PageResult<CinemaVO> listCinemas(String movieId, BigDecimal lat, BigDecimal lng,
                                     Integer radiusMeters, String sort, int page, int size);

    /** 影院详情，附带下属影厅 */
    CinemaVO getCinema(String cinemaId);

    /** 新建影院；cinemaId 冲突则 409 */
    CinemaVO createCinema(CinemaCreateDTO dto);

    /** 更新影院；staff 不可跨院 */
    CinemaVO updateCinema(String cinemaId, CinemaUpdateDTO dto);

    /** 软删影院；仍绑定员工时拒绝 */
    void deleteCinema(String cinemaId);

    /** 创建稀疏座位图并落座位行；情侣座须成对 */
    SeatMapVO createSeatMap(SeatMapCreateDTO dto);

    /** 新建影厅并绑定本影院座位图 */
    HallVO createHall(HallCreateDTO dto);

    /** 运营端影厅分页；admin 必须传 cinemaId */
    PageResult<HallVO> listAdminHalls(String cinemaId, int page, int size);

    /** 仅改影厅名称；校验影院数据范围 */
    HallVO updateHall(String hallId, HallUpdateDTO dto);
}
