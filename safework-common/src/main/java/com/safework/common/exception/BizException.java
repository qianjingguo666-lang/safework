package com.safework.common.exception;

import lombok.Getter;

/**
 * 业务异常：在 Service 里 throw，由全局异常处理器转成统一返回
 */
@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(String msg) {
        this(400, msg);
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
