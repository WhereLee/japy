package com.recloud.common.exception;

import com.recloud.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常（可预知的业务错误）
 * <p>
 * 携带 ResultCode，由 GlobalExceptionHandler 统一捕获并返回给前端。
 * 与系统异常（NullPointerException 等）区分，业务异常不记录 ERROR 日志。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
