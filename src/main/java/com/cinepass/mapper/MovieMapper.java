package com.cinepass.mapper;

import com.cinepass.model.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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

    /** 查询全部影片，用于重建 ES 索引 */
    List<Movie> listAll();

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

    /** 查询已到上映日（release_date <= today）仍未上架的待映影片 ID，供自动上架扫描 */
    List<String> selectReleasedButComingSoon(@Param("today") LocalDate today);

    /** 到上映日自动上架：status=coming_soon 且 release_date <= today → hot_showing，返回翻转行数 */
    int flipComingSoonToShowing(@Param("today") LocalDate today);
}
