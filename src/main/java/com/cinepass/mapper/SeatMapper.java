package com.cinepass.mapper;

import com.cinepass.model.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatMapper {
    List<Seat> selectBySeatMapId(@Param("seatMapId") String seatMapId);

    int insertBatch(@Param("seats") List<Seat> seats);

    int deleteBySeatMapId(@Param("seatMapId") String seatMapId);
}
