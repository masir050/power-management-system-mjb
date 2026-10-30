package com.power.common.pojo;

import lombok.Data;

/**
 * 统一响应结果封装类
 * <p>所有 API 接口均使用此类作为响应体，包含状态码、消息和数据三部分。
 * 提供静态工厂方法快速构建常见响应。</p>
 *
 * @param <T> 响应数据类型
 */
@Data
public class Result<T> {
    /** 状态码：200-成功，400-参数错误，401-未认证，403-无权限，404-不存在，500-服务器错误 */
    private int code;
    /** 响应消息 */
    private String message;
    /** 响应数据 */
    private T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功响应（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /** 成功响应（无数据） */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /** 自定义错误响应 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 参数错误响应（400） */
    public static <T> Result<T> error(String message) {
        return new Result<>(400, message, null);
    }

    /** 未认证响应（401） */
    public static <T> Result<T> unauthorized() {
        return new Result<>(401, "未认证或Token已失效", null);
    }

    /** 无权限响应（403） */
    public static <T> Result<T> forbidden() {
        return new Result<>(403, "无权限访问", null);
    }

    /** 资源不存在响应（404） */
    public static <T> Result<T> notFound() {
        return new Result<>(404, "资源不存在", null);
    }

    /** 服务器内部错误响应（500） */
    public static <T> Result<T> serverError() {
        return new Result<>(500, "服务器内部错误", null);
    }
}
