package com.cinepass.service;

import com.cinepass.dto.ShowCreateDTO;
import com.cinepass.dto.ShowUpdateDTO;
import com.cinepass.vo.ShowVO;

public interface AdminShowService {
    ShowVO create(ShowCreateDTO dto);
    ShowVO update(String showId, ShowUpdateDTO dto);
    ShowVO cancel(String showId);
    ShowVO closeSale(String showId);

    ShowVO resumeSale(String showId);
}
