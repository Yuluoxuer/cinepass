package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 更新购票 Draft：version CAS + patch。
 */
@Data
public class UpdateBookingDraftDTO {

    /** 客户端持有的 Draft 版本 */
    @NotNull(message = "version 不能为空")
    private Long version;

    /** 部分字段补丁；禁止客户端写 lockId/orderId/expireAt */
    @NotNull(message = "patch 不能为空")
    private Map<String, Object> patch;
}
