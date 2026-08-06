package com.cinepass.service.impl;

import com.cinepass.dto.ShowCreateDTO;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.HallMapper;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.SeatMapMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Hall;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
import com.cinepass.service.EsIndexService;
import com.cinepass.service.SeatInventoryService;
import com.cinepass.service.ShowService;
import com.cinepass.vo.ShowVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminShowServiceImpl} 的影院搜索索引同步测试。
 */
class AdminShowServiceImplTest {

    @Test
    void createShowRefreshesCinemaSearchDocument() {
        ShowMapper showMapper = mock(ShowMapper.class);
        MovieMapper movieMapper = mock(MovieMapper.class);
        CinemaMapper cinemaMapper = mock(CinemaMapper.class);
        HallMapper hallMapper = mock(HallMapper.class);
        SeatMapMapper seatMapMapper = mock(SeatMapMapper.class);
        ShowService showService = mock(ShowService.class);
        SeatInventoryService seatInventoryService = mock(SeatInventoryService.class);
        EsIndexService esIndexService = mock(EsIndexService.class);

        when(movieMapper.selectById("movie_1")).thenReturn(new Movie());
        when(cinemaMapper.selectById("cinema_1")).thenReturn(new Cinema());
        Hall hall = new Hall();
        hall.setHallId("hall_1");
        hall.setCinemaId("cinema_1");
        when(hallMapper.selectById("hall_1")).thenReturn(hall);
        when(showMapper.findOverlapping(anyString(), any(), any(), isNull()))
                .thenReturn(Collections.emptyList());
        when(showMapper.selectById(anyString())).thenAnswer(invocation -> {
            ShowSchedule show = new ShowSchedule();
            show.setShowId(invocation.getArgument(0));
            show.setMovieId("movie_1");
            show.setCinemaId("cinema_1");
            show.setHallId("hall_1");
            show.setPrice(new BigDecimal("39.90"));
            return show;
        });
        when(showService.buildShowVO(any(ShowSchedule.class), any()))
                .thenReturn(ShowVO.builder().showId("show_1").build());

        AdminShowServiceImpl service = new AdminShowServiceImpl(
                showMapper, movieMapper, cinemaMapper, hallMapper, seatMapMapper,
                showService, seatInventoryService, esIndexService);
        ShowCreateDTO dto = new ShowCreateDTO();
        dto.setMovieId("movie_1");
        dto.setCinemaId("cinema_1");
        dto.setHallId("hall_1");
        dto.setStartTime(OffsetDateTime.now().plusDays(1).toString());
        dto.setEndTime(OffsetDateTime.now().plusDays(1).plusHours(2).toString());
        dto.setPrice(new BigDecimal("39.90"));

        service.create(dto);

        verify(esIndexService).syncCinema("cinema_1");
    }
}
