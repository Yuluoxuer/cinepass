package com.cinepass.mapper;

import com.cinepass.model.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 座位表 Mapper。
 */
@Mapper
public interface SeatMapper {

    /** 某座位图下全部座位 */
    List<Seat> selectBySeatMapId(@Param("seatMapId") String seatMapId);

    /** 某座位图下按 seatId 列表查询 */
    List<Seat> selectBySeatIds(@Param("seatMapId") String seatMapId,
                               @Param("seatIds") List<String> seatIds);

    /** 批量插入座位 */
    int insertBatch(@Param("seats") List<Seat> seats);

    /** 按座位图删除全部座位 */
    int deleteBySeatMapId(@Param("seatMapId") String seatMapId);
}
