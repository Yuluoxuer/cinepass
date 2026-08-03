package com.cinepass.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 想看关联表 Mapper。
 */
@Mapper
public interface WantSeeMapper {

    /** 插入想看记录；已存在则忽略（幂等） */
    int insertIgnore(@Param("userId") String userId, @Param("movieId") String movieId);

    /** 删除想看记录，返回影响行数 */
    int delete(@Param("userId") String userId, @Param("movieId") String movieId);

    /** 判断用户是否已想看该片 */
    boolean exists(@Param("userId") String userId, @Param("movieId") String movieId);

    /** 分页查询用户想看的电影 ID 列表 */
    List<String> listMovieIds(@Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);

    /** 统计用户想看总数 */
    long countByUser(@Param("userId") String userId);

    /** 查询用户全部想看电影 ID（不分页） */
    List<String> listAllMovieIds(@Param("userId") String userId);
}
