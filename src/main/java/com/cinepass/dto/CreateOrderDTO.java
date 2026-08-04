package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 创建订单入参。
 */
@Data
public class CreateOrderDTO {

    /** 有效锁座凭证 ID；同 lockId 创建幂等 */
    @NotBlank(message = "lockId 不能为空")
    private String lockId;

    /** 可选；Agent/会话 ID，回写 Draft.orderId */
    private String sessionId;
}
