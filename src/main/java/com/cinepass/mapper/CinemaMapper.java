package com.cinepass.mapper;

import com.cinepass.model.Cinema;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 影院表 Mapper。
 */
@Mapper
public interface CinemaMapper {

    /** 按主键查询未软删影院 */
    Cinema selectById(@Param("cinemaId") String cinemaId);

    /** 是否存在（含已软删，用于 ID 冲突检测） */
    boolean exists(@Param("cinemaId") String cinemaId);

    /** 是否存在未软删影院 */
    boolean existsActive(@Param("cinemaId") String cinemaId);

    /**
     * 附近影院分页；可按影片过滤、按距离/最低价排序。
     * {@code distanceMeters}/{@code minPrice} 为计算列。
     */
    List<Cinema> selectNearby(@Param("movieId") String movieId,
                              @Param("lat") BigDecimal lat,
                              @Param("lng") BigDecimal lng,
                              @Param("radiusMeters") Integer radiusMeters,
                              @Param("sort") String sort,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    /** 附近影院总数（与 {@link #selectNearby} 同条件） */
    long countNearby(@Param("movieId") String movieId,
                     @Param("lat") BigDecimal lat,
                     @Param("lng") BigDecimal lng,
                     @Param("radiusMeters") Integer radiusMeters);

    /** 插入影院 */
    int insert(Cinema cinema);

    /** 更新影院基础信息 */
    int update(Cinema cinema);

    /** 软删除；影响行数 1 表示成功 */
    int softDelete(@Param("cinemaId") String cinemaId);
}
