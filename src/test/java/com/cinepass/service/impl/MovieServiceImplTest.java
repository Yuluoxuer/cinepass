package com.cinepass.service.impl;

import com.cinepass.dto.MovieCreateDTO;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.RecoClickMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.Movie;
import com.cinepass.service.EsIndexService;
import com.cinepass.service.EsSearchService;
import com.cinepass.vo.MovieVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MovieServiceImpl} 的搜索索引同步测试。
 */
class MovieServiceImplTest {

    @Test
    void createSynchronizesNewMovieToEs() {
        MovieMapper movieMapper = mock(MovieMapper.class);
        ShowMapper showMapper = mock(ShowMapper.class);
        EsSearchService esSearchService = mock(EsSearchService.class);
        EsIndexService esIndexService = mock(EsIndexService.class);
        RecoClickMapper recoClickMapper = mock(RecoClickMapper.class);
        when(movieMapper.selectById(anyString())).thenAnswer(invocation -> {
            Movie movie = new Movie();
            movie.setMovieId(invocation.getArgument(0));
            movie.setTitle("测试影片");
            movie.setGenresJson("[\"剧情\"]");
            movie.setDurationMin(100);
            movie.setReleaseDate(LocalDate.of(2026, 8, 5));
            movie.setStatus("hot_showing");
            movie.setDescription("测试简介");
            movie.setWantSeeCount(0);
            return movie;
        });

        MovieServiceImpl service = new MovieServiceImpl(
                movieMapper, showMapper, esSearchService, esIndexService, recoClickMapper);
        MovieCreateDTO dto = new MovieCreateDTO();
        dto.setTitle("测试影片");
        dto.setPosterUrl("https://example.com/poster.jpg");
        dto.setGenres(Collections.singletonList("剧情"));
        dto.setDurationMin(100);
        dto.setReleaseDate("2026-08-05");
        dto.setStatus("hot_showing");
        dto.setDescription("测试简介");

        MovieVO result = service.create(dto);

        assertThat(result.getMovieId()).isNotBlank();
        verify(esIndexService).syncMovie(result.getMovieId());
    }
}
