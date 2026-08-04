package com.cinepass.mapper;

import com.cinepass.model.SeatLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 锁座凭证 Mapper（订单写路径依赖）。
 */
@Mapper
public interface SeatLockMapper {

    /** 插入锁座凭证 */
    int insert(SeatLock lock);

    /** 按 lockId 查询并行锁（下单校验归属与有效期） */
    SeatLock findByIdForUpdate(@Param("lockId") String lockId);

    /** 按 lockId 查询（不加锁） */
    SeatLock findById(@Param("lockId") String lockId);

    /** 标记锁座为 released（取消订单/主动解锁） */
    int markReleased(@Param("lockId") String lockId);

    /** 标记锁座为 expired（过期清扫） */
    int markExpired(@Param("lockId") String lockId);
}
