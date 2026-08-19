package com.cinepass.common;

import lombok.Getter;

/**
 * 业务异常；可选携带 data（如 DRAFT_CONFLICT 的 serverDraft）。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final Integer code;

    /** 附加业务载荷；无则为 null */
    private final Object data;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
        this.data = null;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.data = null;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.data = null;
    }

    public BusinessException(ResultCode resultCode, String message, Object data) {
        super(message);
        this.code = resultCode.getCode();
        this.data = data;
    }
}
