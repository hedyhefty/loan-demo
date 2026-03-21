package com.loan.web.exception;

import com.loan.web.common.Result;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Tracer tracer;

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        String traceId = tracer.currentSpan() != null ?
                tracer.currentSpan().context().traceId() : "no-trace";

        log.error("系统异常 [TraceID: {}]", traceId, e);

        return Result.fail(500, "系统繁忙，请稍后再试");
    }
}
