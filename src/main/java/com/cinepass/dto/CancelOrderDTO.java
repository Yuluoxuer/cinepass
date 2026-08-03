package com.cinepass.dto;

import lombok.Data;

/**
 * 取消订单入参（可选）。
 */
@Data
public class CancelOrderDTO {

    /** 取消原因，如 user_cancel */
    private String reason;
}
