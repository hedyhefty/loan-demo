package com.loan.web.exception;

import com.loan.app.exception.BizException;
import com.loan.web.common.Result;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Tracer tracer;

    private String getTraceId() {
        return tracer.currentSpan() != null ?
                tracer.currentSpan().context().traceId() : null;
    }

    /**
     * 处理业务异常（如：额度不足、订单重复）
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage(), getTraceId());
    }

    /**
     * 处理参数校验异常（如：@NotNull 校验失败）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        FieldError firstError = bindingResult.getFieldError();
        String errorMsg = firstError != null ?
                firstError.getField() + " " + firstError.getDefaultMessage() : "参数格式错误";

        log.warn("参数校验失败: {}", errorMsg);
        return Result.fail(400, errorMsg, getTraceId());
    }

    /**
     * 处理系统级别/未知异常（如：空指针、数据库宕机）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统严重异常，请排查:", e);
        return Result.fail(500, "系统繁忙，请稍后再试", getTraceId());
    }
}
