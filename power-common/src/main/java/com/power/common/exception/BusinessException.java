package com.power.common.exception;

import lombok.Getter;

/**
 * 业务异常类
 * <p>当业务逻辑校验失败时抛出，包含错误码和描述信息，
 * 由 GlobalExceptionHandler 统一捕获并返回给前端</p>
 */
@Getter
public class BusinessException extends RuntimeException {
    /** 错误码 */
    private final int code;

    /**
     * 构造指定错误码和消息的业务异常
     * @param code    错误码
     * @param message 错误描述
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造错误码为400的业务异常（通用参数错误）
     * @param message 错误描述
     */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }
}
