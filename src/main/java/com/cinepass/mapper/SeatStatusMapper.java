package com.cinepass.mapper;

import com.cinepass.model.SeatStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 场次座位状态 Mapper。
 */
@Mapper
public interface SeatStatusMapper {

    /** 排片时批量初始化座位状态 */
    int batchInsert(@Param("list") List<SeatStatus> statuses);

    /** 可售余座数 */
    int countAvailable(@Param("showId") String showId);

    /** 总座位数 */
    int countTotal(@Param("showId") String showId);

    /** 按场次查询全部座位状态 */
    List<SeatStatus> selectByShowId(@Param("showId") String showId);

    /**
     * 按场次+座位列表行锁查询（PG FOR UPDATE；H2 无行锁）。
     * 调用方应对 seatIds 排序以避免死锁。
     */
    List<SeatStatus> selectForUpdate(@Param("showId") String showId,
                                     @Param("seatIds") List<String> seatIds);

    /** 将已过期 locked 座位释放为 available（指定座位） */
    int releaseExpired(@Param("showId") String showId,
                       @Param("seatIds") List<String> seatIds,
                       @Param("now") OffsetDateTime now);

    /** 将本场全部已过期 locked 座位释放为 available（读座位图前调用） */
    int releaseExpiredByShow(@Param("showId") String showId,
                             @Param("now") OffsetDateTime now);

    /** 将指定座位标为 locked */
    int markLocked(@Param("showId") String showId,
                   @Param("seatIds") List<String> seatIds,
                   @Param("lockId") String lockId,
                   @Param("userId") String userId,
                   @Param("expireAt") OffsetDateTime expireAt);

    /** 按 lockId 释放仍为 locked 的座位（取消订单/解锁） */
    int releaseByLockId(@Param("lockId") String lockId);
}
