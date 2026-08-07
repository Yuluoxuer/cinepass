package com.cinepass.dto;

import lombok.Data;

/**
 * 确认支付入参（系分 §6.2.3）。
 */
@Data
public class PayDTO {

    /** 支付渠道：desktop_button | mobile_qr；不传默认 desktop_button（审计用） */
    private String channel;
}
