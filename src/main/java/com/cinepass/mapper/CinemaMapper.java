package com.cinepass.mapper;

import com.cinepass.model.Cinema;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CinemaMapper {
    Cinema selectById(@Param("cinemaId") String cinemaId);

    boolean exists(@Param("cinemaId") String cinemaId);

    boolean existsActive(@Param("cinemaId") String cinemaId);

    List<Cinema> selectNearby(@Param("movieId") String movieId,
                              @Param("lat") BigDecimal lat,
                              @Param("lng") BigDecimal lng,
                              @Param("radiusMeters") Integer radiusMeters,
                              @Param("sort") String sort,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    long countNearby(@Param("movieId") String movieId,
                     @Param("lat") BigDecimal lat,
                     @Param("lng") BigDecimal lng,
                     @Param("radiusMeters") Integer radiusMeters);

    int insert(Cinema cinema);

    int update(Cinema cinema);

    int softDelete(@Param("cinemaId") String cinemaId);
}
