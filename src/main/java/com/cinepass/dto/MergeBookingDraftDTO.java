package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 合并购票 Draft 入参：Agent 写回完整草稿（决策字段 + 锁/单成果）。
 * 与 PUT（patch CAS）不同，merge 接受 Agent 侧整份草稿，
 * 服务端保护 lockId/orderId/expireAt 不被空值冲掉，version 取 max(server, incoming)+1。
 */
@Data
public class MergeBookingDraftDTO {

    /** Agent 侧完整草稿（含 version）；禁止字段（lockId/orderId/expireAt）由服务端保护 */
    @NotNull(message = "draft 不能为空")
    private Map<String, Object> draft;
}
