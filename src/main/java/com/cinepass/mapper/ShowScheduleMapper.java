package com.cinepass.mapper;

import com.cinepass.model.SeatPriceRow;
import com.cinepass.model.ShowSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 场次快照 / 分区价查询（下单用）。
 */
@Mapper
public interface ShowScheduleMapper {

    /** 场次 + 影片/影院/厅名称快照，写入订单冗余字段 */
    ShowSnapshot findSnapshot(@Param("showId") String showId);

    /** 按座位 ID 列表查价区与单价 */
    List<SeatPriceRow> listSeatPrices(@Param("showId") String showId,
                                      @Param("seatIds") List<String> seatIds);
}
