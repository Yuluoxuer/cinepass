package com.cinepass.service;

import com.cinepass.model.ShowSchedule;
import com.cinepass.vo.ShowDetailVO;
import com.cinepass.vo.ShowListResult;
import com.cinepass.vo.ShowVO;

import java.util.List;

public interface ShowService {
    ShowListResult list(String cinemaId, String movieId, String date);

    ShowListResult listAll(String cinemaId, String movieId);
    ShowDetailVO get(String showId);
    ShowVO buildShowVO(ShowSchedule s);
    ShowVO buildShowVO(ShowSchedule s, List<ShowVO.ZonePriceVO> zonePrices);
}
