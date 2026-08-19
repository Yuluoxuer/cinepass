package com.cinepass.controller;

import com.cinepass.aop.RateLimit;
import com.cinepass.common.Result;
import com.cinepass.dto.CreateLockDTO;
import com.cinepass.security.LoginUser;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.LockService;
import com.cinepass.vo.LockVO;
import com.cinepass.vo.UnlockResultVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * C 端锁座接口（系分 §5.2–5.4）。
 * <pre>
 * POST   /api/v1/locks
 * GET    /api/v1/locks/{lockId}
 * DELETE /api/v1/locks/{lockId}?sessionId=
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/locks")
public class LockController {

    private final LockService lockService;

    public LockController(LockService lockService) {
        this.lockService = lockService;
    }

    /**
     * 锁座；Header Idempotency-Key 可选——传入时同 key 重复请求返回首次结果（Redis 缓存 900s）
     */
    @RateLimit(key = "lock", permits = 15, windowSeconds = 60)
    @PostMapping
    public Result<LockVO> create(
            @Valid @RequestBody CreateLockDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(lockService.create(SecurityContext.getCurrentUserId(), dto, idempotencyKey));
    }

    /** 查询锁座；仅本人 */
    @GetMapping("/{lockId}")
    @LoginUser
    public Result<LockVO> get(@PathVariable String lockId) {
        return Result.success(lockService.get(SecurityContext.getCurrentUserId(), lockId));
    }

    /** 释放锁座；幂等；可选 sessionId 清 Draft */
    @RateLimit(key = "lock", permits = 15, windowSeconds = 60)
    @DeleteMapping("/{lockId}")
    @LoginUser
    public Result<UnlockResultVO> unlock(@PathVariable String lockId,
                                         @RequestParam(required = false) String sessionId) {
        return Result.success(lockService.unlock(SecurityContext.getCurrentUserId(), lockId, sessionId));
    }
}
