package com.cinepass.mapper;

import com.cinepass.model.Cinema;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 内部Mapper — 仅供Show模块查询影院基本信息，非独立模块。
 */
@Mapper
public interface CinemaMapper {
    Cinema selectById(@Param("cinemaId") String cinemaId);

    List<Cinema> listAll(@Param("offset") int offset, @Param("limit") int limit);

    int insert(@Param("cinema") Cinema cinema);
}
