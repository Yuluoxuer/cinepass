package com.cinepass.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 座位库存 Mapper（取消订单释放锁座）。
 */
@Mapper
public interface SeatStatusMapper {

    /** 按 lockId 释放座位占用，返回影响行数 */
    int releaseByLockId(@Param("lockId") String lockId);
}
