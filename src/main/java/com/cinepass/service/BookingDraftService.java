package com.cinepass.service;

import com.cinepass.dto.CreateBookingDraftDTO;
import com.cinepass.dto.UpdateBookingDraftDTO;
import com.cinepass.vo.BookingDraftVO;

/**
 * 购票 Draft（系分 §4 / §7）：创建、hydrate、CAS 更新。
 */
public interface BookingDraftService {

    /** 创建新会话 Draft */
    BookingDraftVO create(CreateBookingDraftDTO dto, String currentUserId);

    /**
     * 获取 Draft；不存在则懒创建 Idle。
     * 已绑定 userId 且含锁/单时仅本人可看。
     */
    BookingDraftVO get(String sessionId, String currentUserId);

    /** CAS 更新；version 冲突抛 DRAFT_CONFLICT */
    BookingDraftVO update(String sessionId, UpdateBookingDraftDTO dto, String currentUserId);

    /**
     * 锁座成功后回写 Draft：lockId/seatIds/expireAt，state=ConfirmOrder，version++。
     * session 不存在则懒创建后再绑定；须可写（同 update 权限）。
     */
    void bindLock(String sessionId, String lockId, java.util.List<String> seatIds,
                  String expireAt, String userId);

    /**
     * 解锁后清空 Draft 的 lock/seat/expire；仅当 draft.lockId 与传入 lockId 一致时生效。
     */
    void clearLockFields(String sessionId, String lockId, String userId);
}
