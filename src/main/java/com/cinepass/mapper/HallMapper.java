package com.cinepass.mapper;

import com.cinepass.model.Hall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HallMapper {
    Hall selectById(@Param("hallId") String hallId);

    List<Hall> selectByCinemaId(@Param("cinemaId") String cinemaId);

    List<Hall> selectAdminByCinemaId(@Param("cinemaId") String cinemaId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    long countByCinemaId(@Param("cinemaId") String cinemaId);

    int insert(Hall hall);

    int updateName(@Param("hallId") String hallId, @Param("name") String name);
}
