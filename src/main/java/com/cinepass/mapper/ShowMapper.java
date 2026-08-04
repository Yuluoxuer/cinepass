package com.cinepass.mapper;

import com.cinepass.model.ShowSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface ShowMapper {
    ShowSchedule selectById(@Param("showId") String showId);

    List<ShowSchedule> listByMovieCinemaDate(@Param("cinemaId") String cinemaId,
                                              @Param("movieId") String movieId,
                                              @Param("date") String date);

    List<ShowSchedule> listByMovieCinema(@Param("cinemaId") String cinemaId,
                                          @Param("movieId") String movieId);

    List<ShowSchedule> findOverlapping(@Param("hallId") String hallId,
                                        @Param("startTime") OffsetDateTime startTime,
                                        @Param("endTime") OffsetDateTime endTime,
                                        @Param("excludeShowId") String excludeShowId);

    int insert(@Param("show") ShowSchedule show);

    int update(@Param("show") ShowSchedule show);

    int cancel(@Param("showId") String showId);

    int countActiveLocksOrOrders(@Param("showId") String showId);
    int closeSale(@Param("showId") String showId);

    int resumeSale(@Param("showId") String showId);
}
