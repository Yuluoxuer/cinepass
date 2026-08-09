package com.cinepass.service.impl;

import com.cinepass.common.BusinessException;
import com.cinepass.dto.ShowBatchCreateDTO;
import com.cinepass.dto.ShowCreateDTO;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.HallMapper;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.SeatMapMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Hall;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
import com.cinepass.security.Roles;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.EsIndexService;
import com.cinepass.service.SeatInventoryService;
import com.cinepass.service.ShowService;
import com.cinepass.vo.ShowVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @BeforeEach
    void setUp() {
        SecurityContext.clear();
        // create() 首行 assertCinemaScope 需要身份上下文：以 admin 身份绕过 staff 影院范围校验
        SecurityContext.set("admin_1", "系统管理员", null, null,
                Collections.singletonList(Roles.ADMIN), null);
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

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
                showService, seatInventoryService, esIndexService,
                mock(UserAccountMapper.class));
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

    @Test
    void createAndBatchCreate_rejectOffMovie() {
        ShowMapper showMapper = mock(ShowMapper.class);
        MovieMapper movieMapper = mock(MovieMapper.class);
        CinemaMapper cinemaMapper = mock(CinemaMapper.class);
        HallMapper hallMapper = mock(HallMapper.class);
        SeatMapMapper seatMapMapper = mock(SeatMapMapper.class);
        ShowService showService = mock(ShowService.class);
        SeatInventoryService seatInventoryService = mock(SeatInventoryService.class);
        EsIndexService esIndexService = mock(EsIndexService.class);

        Movie offMovie = new Movie();
        offMovie.setMovieId("movie_off");
        offMovie.setTitle("已下架片");
        offMovie.setStatus("off");
        when(movieMapper.selectById("movie_off")).thenReturn(offMovie);

        AdminShowServiceImpl service = new AdminShowServiceImpl(
                showMapper, movieMapper, cinemaMapper, hallMapper, seatMapMapper,
                showService, seatInventoryService, esIndexService,
                mock(UserAccountMapper.class));

        // 单个创建：下架片被拒
        ShowCreateDTO createDto = new ShowCreateDTO();
        createDto.setMovieId("movie_off");
        createDto.setCinemaId("cinema_1");
        createDto.setHallId("hall_1");
        createDto.setStartTime(OffsetDateTime.now().plusDays(1).toString());
        createDto.setEndTime(OffsetDateTime.now().plusDays(1).plusHours(2).toString());
        assertThatThrownBy(() -> service.create(createDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下架");

        // 批量创建：下架片被拒
        ShowBatchCreateDTO batchDto = new ShowBatchCreateDTO();
        batchDto.setMovieId("movie_off");
        batchDto.setCinemaId("cinema_1");
        batchDto.setHallId("hall_1");
        batchDto.setDateStart("2026-08-11");
        batchDto.setDateEnd("2026-08-11");
        batchDto.setTimeStart("10:00");
        batchDto.setTimeEnd("22:00");
        batchDto.setIntervalMin(120);
        assertThatThrownBy(() -> service.batchCreate(batchDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下架");
    }
}
