package com.cinepass.mapper;

import com.cinepass.model.ShowZonePrice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 场次分区价 Mapper。
 */
@Mapper
public interface ShowZonePriceMapper {

    /** 某场次全部分区价 */
    List<ShowZonePrice> selectByShowId(@Param("showId") String showId);

    /** 批量按场次查询（运营列表组装用） */
    List<ShowZonePrice> selectByShowIds(@Param("showIds") List<String> showIds);

    /** 删除某场次全部分区价（换绑/全量替换前） */
    int deleteByShowId(@Param("showId") String showId);

    /** 批量插入分区价 */
    int insertBatch(@Param("rows") List<ShowZonePrice> rows);
}
