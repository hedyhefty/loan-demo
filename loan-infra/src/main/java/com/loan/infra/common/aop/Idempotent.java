package com.loan.infra.common.aop;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /**
     * SpEL 表达式，例如 "#event.orderNo"
     */
    String key();

    String prefix() default "idempotent:lock:";

    int expire() default 60;
}
