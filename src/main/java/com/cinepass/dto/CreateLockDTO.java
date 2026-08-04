package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 锁座入参（{@code POST /api/v1/locks}）。
 */
@Data
public class CreateLockDTO {

    /** 场次 ID */
    @NotBlank(message = "showId 不能为空")
    private String showId;

    /** 系统 seatId 列表，长度 1–4 */
    @NotEmpty(message = "seatIds 不能为空")
    @Size(min = 1, max = 4, message = "seatIds 长度须为 1–4")
    private List<String> seatIds;

    /** 锁座 TTL（秒）；默认 900，范围 60–900 */
    private Integer ttlSeconds;

    /** 可选；传入则回写 BookingDraft 锁字段 */
    private String sessionId;
}
