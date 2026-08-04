package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.dto.CreateBookingDraftDTO;
import com.cinepass.dto.UpdateBookingDraftDTO;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.BookingDraftService;
import com.cinepass.vo.BookingDraftVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 购票 Draft REST（系分 §7；公开可匿名，登录则绑定 userId）。
 * <pre>
 * POST /api/v1/booking-drafts
 * GET  /api/v1/booking-drafts/{sessionId}
 * PUT  /api/v1/booking-drafts/{sessionId}
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/booking-drafts")
public class BookingDraftController {

    private final BookingDraftService bookingDraftService;

    public BookingDraftController(BookingDraftService bookingDraftService) {
        this.bookingDraftService = bookingDraftService;
    }

    /** 创建 Draft（公开） */
    @PostMapping
    public Result<BookingDraftVO> create(@RequestBody(required = false) @Valid CreateBookingDraftDTO dto) {
        if (dto == null) {
            dto = new CreateBookingDraftDTO();
        }
        return Result.success(bookingDraftService.create(dto, SecurityContext.getCurrentUserId()));
    }

    /** hydrate；不存在懒创建 Idle */
    @GetMapping("/{sessionId}")
    public Result<BookingDraftVO> get(@PathVariable String sessionId) {
        return Result.success(bookingDraftService.get(sessionId, SecurityContext.getCurrentUserId()));
    }

    /** CAS 更新；冲突返回 DRAFT_CONFLICT + serverDraft */
    @PutMapping("/{sessionId}")
    public Result<BookingDraftVO> update(@PathVariable String sessionId,
                                         @Valid @RequestBody UpdateBookingDraftDTO dto) {
        return Result.success(bookingDraftService.update(sessionId, dto, SecurityContext.getCurrentUserId()));
    }
}
