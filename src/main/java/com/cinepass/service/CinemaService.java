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

public interface CinemaService {
    PageResult<CinemaVO> listCinemas(String movieId, BigDecimal lat, BigDecimal lng,
                                     Integer radiusMeters, String sort, int page, int size);
    CinemaVO getCinema(String cinemaId);
    CinemaVO createCinema(CinemaCreateDTO dto);
    CinemaVO updateCinema(String cinemaId, CinemaUpdateDTO dto);
    void deleteCinema(String cinemaId);
    SeatMapVO createSeatMap(SeatMapCreateDTO dto);
    HallVO createHall(HallCreateDTO dto);
    PageResult<HallVO> listAdminHalls(String cinemaId, int page, int size);
    HallVO updateHall(String hallId, HallUpdateDTO dto);
}
