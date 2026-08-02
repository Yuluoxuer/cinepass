package com.cinepass.mapper;

import com.cinepass.model.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MovieMapper {
    boolean exists(@Param("movieId") String movieId);
    List<Movie> selectByIds(@Param("ids") List<String> ids);
    int incrWantSeeCount(@Param("movieId") String movieId, @Param("delta") int delta);
}
