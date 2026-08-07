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
    ORDER_NOT_PAYABLE(4103, "订单不可支付"),
    ORDER_NOT_REDEEMABLE(4104, "订单不可核销"),

    /** 支付二维码 payToken 验签失败或与订单不匹配（系分 errorCode=PAY_TOKEN_INVALID） */
    PAY_TOKEN_INVALID(4105, "支付令牌无效"),

    /** 支付二维码 payToken 已过期或已消费（系分 errorCode=PAY_TOKEN_EXPIRED） */
    PAY_TOKEN_EXPIRED(4106, "支付令牌已过期"),

    /** 核销二维码 redeemToken 验签失败或与订单不匹配 */
    REDEEM_TOKEN_INVALID(4107, "核销令牌无效"),

    /** 核销二维码 redeemToken 已过期 */
    REDEEM_TOKEN_EXPIRED(4108, "核销令牌已过期"),

    /** Draft CAS 版本冲突（系分 errorCode=DRAFT_CONFLICT） */
    DRAFT_CONFLICT(-1, "draft version mismatch"),

    /** 座位已被锁/已售（系分 errorCode=SEAT_TAKEN） */
    SEAT_TAKEN(-1, "seats already taken"),

    /** 情侣座须成对选择（系分 errorCode=COUPLE_RULE） */
    COUPLE_RULE(-1, "couple seats must be selected together"),

    /** 座位不属于该场次座位图（系分 errorCode=SEAT_INVALID） */
    SEAT_INVALID(-1, "seat does not belong to show"),
    ;

    private final int code;
    private final String message;
}
