package com.cinepass.mapper;

import com.cinepass.model.SeatStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatStatusMapper {
    int batchInsert(@Param("list") List<SeatStatus> statuses);

    int countAvailable(@Param("showId") String showId);

    int countTotal(@Param("showId") String showId);
}
