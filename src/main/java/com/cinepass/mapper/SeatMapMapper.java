package com.cinepass.mapper;

import com.cinepass.model.SeatMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 座位图表 Mapper。
 */
@Mapper
public interface SeatMapMapper {

    /** 按主键查询 */
    SeatMap selectById(@Param("seatMapId") String seatMapId);

    /** 某影院座位图分页 */
    List<SeatMap> selectByCinemaId(@Param("cinemaId") String cinemaId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    /** 某影院座位图总数 */
    long countByCinemaId(@Param("cinemaId") String cinemaId);

    /** 插入座位图元数据 */
    int insert(SeatMap seatMap);

    /** 更新座位图元数据 */
    int update(SeatMap seatMap);

    /** 标记不可变（已绑厅排片后） */
    int markImmutable(@Param("seatMapId") String seatMapId);

    /** 物理删除座位图元数据 */
    int deleteById(@Param("seatMapId") String seatMapId);
}
