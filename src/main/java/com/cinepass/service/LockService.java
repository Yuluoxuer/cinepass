package com.cinepass.service;

import com.cinepass.dto.CreateLockDTO;
import com.cinepass.vo.LockVO;
import com.cinepass.vo.UnlockResultVO;

/**
 * 锁座：创建、查询、释放（系分 §5.2–5.4）。
 */
public interface LockService {

    /**
     * 锁座；须登录。情侣座成对校验；行锁后非 available 抛 SEAT_TAKEN。
     * 可选 sessionId 回写 Draft（state=ConfirmOrder）。
     */
    LockVO create(String userId, CreateLockDTO dto);

    /** 查询锁座；仅本人 */
    LockVO get(String userId, String lockId);

    /**
     * 释放锁座；仅本人。已 released/expired 幂等返回 released=true。
     * 可选 sessionId 清空 Draft 锁字段。
     */
    UnlockResultVO unlock(String userId, String lockId, String sessionId);
}
