package com.loan.app.funding;

import com.loan.domain.order.entity.LoanOrder;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 放款客户端实现（带熔断保护）
 * <p>使用 Resilience4j 的 @CircuitBreaker 注解保护外部调用，
 * 当失败率超过阈值时自动熔断，防止雪崩。
 */
@Slf4j
@Component
public class ResilientFundingClient implements FundingClient {

    private static final String CB_NAME = "fundingService";

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "applyFallback")
    public FundingResult apply(LoanOrder order) {
        log.info("【放款平台】发起放款请求: orderNo={}, amount={}",
                order.getOrderNo(), order.getAmount());

        // 模拟调用外部银行接口，耗时 1-2 秒
        simulateExternalCall();

        log.info("【放款平台】放款请求发送成功: orderNo={}", order.getOrderNo());
        return FundingResult.SUCCESS;
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "queryStatusFallback")
    public FundingResult queryStatus(String orderNo) {
        // 模拟查询银行状态
        simulateExternalCall();
        return FundingResult.SUCCESS;
    }

    /**
     * 熔断降级方法：apply 失败时的兜底逻辑
     */
    public FundingResult applyFallback(LoanOrder order, Throwable t) {
        log.error("【熔断触发】放款请求被拦截: orderNo={}, 原因={}, error={}",
                order.getOrderNo(), t.getClass().getSimpleName(), t.getMessage());
        // 降级返回 PROCESSING，对账服务会后续处理
        return FundingResult.PROCESSING;
    }

    /**
     * 熔断降级方法：queryStatus 失败时的兜底逻辑
     */
    public FundingResult queryStatusFallback(String orderNo, Throwable t) {
        log.error("【熔断触发】查询放款状态被拦截: orderNo={}, 原因={}, error={}",
                orderNo, t.getClass().getSimpleName(), t.getMessage());
        return FundingResult.PROCESSING;
    }

    /**
     * 模拟外部银行接口调用（耗时 1-2 秒）
     * <p>压测时可注入真实银行 SDK
     */
    private void simulateExternalCall() {
        try {
            long ms = 1000 + (long) (Math.random() * 1000);
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
