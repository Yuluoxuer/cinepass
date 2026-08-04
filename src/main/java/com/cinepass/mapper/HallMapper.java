package com.cinepass.mapper;

import com.cinepass.model.Hall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 影厅表 Mapper。
 */
@Mapper
public interface HallMapper {

    /** 按主键查询 */
    Hall selectById(@Param("hallId") String hallId);

    /** 某影院下全部影厅（C 端详情用） */
    List<Hall> selectByCinemaId(@Param("cinemaId") String cinemaId);

    /** 运营端分页列表 */
    List<Hall> selectAdminByCinemaId(@Param("cinemaId") String cinemaId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /** 某影院影厅总数 */
    long countByCinemaId(@Param("cinemaId") String cinemaId);

    /** 插入影厅 */
    int insert(Hall hall);

    /** 仅更新影厅名称 */
    int updateName(@Param("hallId") String hallId, @Param("name") String name);
}
