package com.cinepass.mapper;

import com.cinepass.model.Hall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 内部Mapper — 仅供Show模块查询影厅信息（hallName、seatMapId、所属影院），非独立模块。
 */
@Mapper
public interface HallMapper {
    Hall selectById(@Param("hallId") String hallId);

    List<Hall> selectByCinemaId(@Param("cinemaId") String cinemaId);

    int insert(@Param("hall") Hall hall);
}
