package com.cinepass.mapper;

import com.cinepass.model.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 电影表 Mapper（想看等业务依赖的查询与计数）。
 */
@Mapper
public interface MovieMapper {

    /** 判断影片是否存在 */
    boolean exists(@Param("movieId") String movieId);

    /** 按 ID 列表批量查询影片 */
    List<Movie> selectByIds(@Param("ids") List<String> ids);

    /** 增减想看计数（delta 可为负） */
    int incrWantSeeCount(@Param("movieId") String movieId, @Param("delta") int delta);
}
