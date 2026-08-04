package com.cinepass.service;

import com.cinepass.dto.MovieCreateDTO;
import com.cinepass.dto.MovieUpdateDTO;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;

public interface MovieService {
    PageResult<MovieVO> page(String status, String q, String genre, int page, int size);
    MovieVO get(String movieId);
    MovieVO create(MovieCreateDTO dto);
    MovieVO update(String movieId, MovieUpdateDTO dto);
}
