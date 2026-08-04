package com.cinepass.mapper;

import com.cinepass.model.ShowSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 场次表 Mapper。
 */
@Mapper
public interface ShowMapper {

    /** 按主键查询 */
    ShowSchedule selectById(@Param("showId") String showId);

    /** 影院+影片+日期场次列表 */
    List<ShowSchedule> listByMovieCinemaDate(@Param("cinemaId") String cinemaId,
                                              @Param("movieId") String movieId,
                                              @Param("date") String date);

    /** 影院+影片全部场次 */
    List<ShowSchedule> listByMovieCinema(@Param("cinemaId") String cinemaId,
                                          @Param("movieId") String movieId);

    /**
     * 影院在 {@code after} 之后仍有 on_sale 场次的影片：每片取最早一场。
     * 仅填充 {@code movieId}、{@code startTime}。
     */
    List<ShowSchedule> listEarliestUpcomingByCinema(@Param("cinemaId") String cinemaId,
                                                     @Param("after") OffsetDateTime after);

    /**
     * 同厅时间重叠检测（含清场缓冲由调用方扩展 endTime）。
     * {@code excludeShowId} 用于改期时排除自身。
     */
    List<ShowSchedule> findOverlapping(@Param("hallId") String hallId,
                                        @Param("startTime") OffsetDateTime startTime,
                                        @Param("endTime") OffsetDateTime endTime,
                                        @Param("excludeShowId") String excludeShowId);

    /** 插入场次 */
    int insert(@Param("show") ShowSchedule show);

    /** 更新场次 */
    int update(@Param("show") ShowSchedule show);

    /** 取消场次 */
    int cancel(@Param("showId") String showId);

    /** 统计在途锁座或有效订单数（改期前置校验） */
    int countActiveLocksOrOrders(@Param("showId") String showId);

    /** 停售 */
    int closeSale(@Param("showId") String showId);

    /** 恢复开售 */
    int resumeSale(@Param("showId") String showId);
}
