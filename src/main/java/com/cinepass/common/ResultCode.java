package com.cinepass.common;

import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * Generic response status codes.
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(400, "操作失败"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    ERROR(500, "服务器内部错误"),

    BUSINESS_ERROR(4000, "业务异常"),
    PARAM_ERROR(4001, "参数校验失败"),

    UNAUTHORIZED_TOKEN(40101, "未认证或令牌无效"),
    FORBIDDEN_PERMISSION(40301, "权限不足"),

    LOCK_EXPIRED(4101, "锁座已失效或不可用"),
    ORDER_NOT_CANCELLABLE(4102, "订单不可取消"),
    ;

    private final int code;
    private final String message;
}
