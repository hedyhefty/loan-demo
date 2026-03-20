package com.loan.web.exception;

import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    // Spring Boot 3 自动注入的链路追踪器
    private final Tracer tracer;

    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception e) {
        // 从当前上下文中获取 traceId
        String traceId = tracer.currentSpan() != null ?
                tracer.currentSpan().context().traceId() : "no-trace";

        log.error("系统异常 [TraceID: {}]", traceId, e);

        return Map.of(
                "code", 500,
                "message", "系统繁忙，请稍后再试",
                "traceId", traceId
        );
    }
}