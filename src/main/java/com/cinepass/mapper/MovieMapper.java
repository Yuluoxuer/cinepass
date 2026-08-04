package com.cinepass.mapper;

import com.cinepass.model.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 电影表 Mapper。
 */
@Mapper
public interface MovieMapper {

    /** 判断影片是否存在 */
    boolean exists(@Param("movieId") String movieId);

    /** 按主键查询 */
    Movie selectById(@Param("movieId") String movieId);

    /** 批量按 ID 查询 */
    List<Movie> selectByIds(@Param("ids") List<String> ids);

    /** 条件分页列表 */
    List<Movie> listFiltered(@Param("status") String status,
                             @Param("q") String q,
                             @Param("genre") String genre,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    /** 条件计数 */
    long countFiltered(@Param("status") String status,
                       @Param("q") String q,
                       @Param("genre") String genre);

    /** 插入影片 */
    int insert(@Param("movie") Movie movie);

    /** 更新影片 */
    int update(@Param("movie") Movie movie);

    /** 想看人数加减；delta 可为负 */
    int incrWantSeeCount(@Param("movieId") String movieId, @Param("delta") int delta);
}
