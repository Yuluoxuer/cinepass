package com.cinepass.mapper;

import com.cinepass.model.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MovieMapper {
    boolean exists(@Param("movieId") String movieId);

    Movie selectById(@Param("movieId") String movieId);

    List<Movie> selectByIds(@Param("ids") List<String> ids);

    List<Movie> listFiltered(@Param("status") String status,
                             @Param("q") String q,
                             @Param("genre") String genre,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    long countFiltered(@Param("status") String status,
                       @Param("q") String q,
                       @Param("genre") String genre);

    int insert(@Param("movie") Movie movie);

    int update(@Param("movie") Movie movie);

    int incrWantSeeCount(@Param("movieId") String movieId, @Param("delta") int delta);
}
