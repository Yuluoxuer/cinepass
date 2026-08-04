package com.cinepass.mapper;

import com.cinepass.model.SeatMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatMapMapper {
    SeatMap selectById(@Param("seatMapId") String seatMapId);

    List<SeatMap> selectByCinemaId(@Param("cinemaId") String cinemaId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countByCinemaId(@Param("cinemaId") String cinemaId);

    int insert(SeatMap seatMap);

    int update(SeatMap seatMap);

    int markImmutable(@Param("seatMapId") String seatMapId);

    int deleteById(@Param("seatMapId") String seatMapId);
}
