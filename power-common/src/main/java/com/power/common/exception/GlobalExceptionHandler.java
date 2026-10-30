package com.power.common.exception;

import com.power.common.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>通过 @RestControllerAdvice 统一拦截各层抛出的异常，将其转换为统一的 Result 响应格式
 * 返回给前端，避免直接暴露堆栈信息。</p>
 *
 * <p>异常必须落日志，否则线上只能看到"服务器内部错误"，无从定位。
 * 业务异常与参数异常按 WARN 记录（预期内），未知异常按 ERROR 记录并打印堆栈。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常（如用户名不存在、欠费校验失败等）
     *
     * @param e 业务异常对象
     * @return 包含业务错误码和消息的统一响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理 @RequestBody + @Valid 触发的参数校验异常（如登录用户名不能为空）
     *
     * @param e 参数校验异常
     * @return 400 错误响应，message 汇总所有字段的校验失败原因
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理表单/查询参数绑定校验异常
     *
     * @param e 绑定异常
     * @return 400 错误响应
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数绑定校验失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理缺少必填请求参数
     *
     * @param e 参数缺失异常
     * @return 400 错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return Result.error(400, "缺少必填参数: " + e.getParameterName());
    }

    /**
     * 处理参数类型不匹配（如把 abc 传给 Long 类型的 id）
     *
     * @param e 类型不匹配异常
     * @return 400 错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: name={} value={}", e.getName(), e.getValue());
        return Result.error(400, "参数格式错误: " + e.getName());
    }

    /**
     * 处理请求体 JSON 解析失败（字段类型对不上、JSON 语法错误）
     *
     * @param e 报文解析异常
     * @return 400 错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(400, "请求体格式错误，请检查字段类型");
    }

    /**
     * 处理唯一索引冲突（如重复用户名）
     *
     * @param e 主键/唯一键冲突异常
     * @return 400 错误响应
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMessage());
        return Result.error(400, "数据已存在，请勿重复提交");
    }

    /**
     * 处理参数校验类非法参数异常
     *
     * @param e 非法参数异常
     * @return 400 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * 处理越权访问（客户看别人的账单、运维改账单等）
     *
     * <p>返回 403 而不是 500：越权是"你不该看"，不是"系统坏了"。
     * 前端拿到 403 给提示，不会弹"服务器内部错误"这种吓人的东西。</p>
     */
    @ExceptionHandler(com.power.common.security.DataScope.ForbiddenException.class)
    public org.springframework.http.ResponseEntity<Result<Void>> handleForbidden(
            com.power.common.security.DataScope.ForbiddenException e) {
        log.warn("越权访问被拒绝: {}", e.getMessage());
        return org.springframework.http.ResponseEntity.status(403)
                .body(Result.error(403, e.getMessage()));
    }

    /**
     * 处理未捕获的运行时异常（兜底处理）
     * <p>必须打印堆栈，否则线上无法定位问题源头。</p>
     *
     * @param e 异常对象
     * @return 500 服务器内部错误响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未处理异常", e);
        return Result.serverError();
    }

    /** 把字段校验错误格式化成 "字段名: 原因" */
    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
