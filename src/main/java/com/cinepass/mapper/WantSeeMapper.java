package com.cinepass.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WantSeeMapper {
    int insertIgnore(@Param("userId") String userId, @Param("movieId") String movieId);
    int delete(@Param("userId") String userId, @Param("movieId") String movieId);
    boolean exists(@Param("userId") String userId, @Param("movieId") String movieId);
    List<String> listMovieIds(@Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);
    long countByUser(@Param("userId") String userId);
    List<String> listAllMovieIds(@Param("userId") String userId);
}
