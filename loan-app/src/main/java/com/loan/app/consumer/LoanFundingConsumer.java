package com.loan.app.consumer;

import com.loan.app.config.RabbitConfig;
import com.loan.app.event.LoanOrderCreatedEvent;
import com.loan.domain.order.entity.LoanOrder;
import com.loan.domain.order.enums.OrderStatus;
import com.loan.domain.order.repository.LoanOrderRepository;
import com.loan.infra.common.aop.Idempotent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanFundingConsumer {

    private final LoanOrderRepository orderRepository;

    @Idempotent(key = "#event.orderNo", prefix = "lock:funding:", expire = 300)
    @RabbitListener(queues = RabbitConfig.FUNDING_QUEUE)
    @Transactional
    public void handleFunding(LoanOrderCreatedEvent event) {
        log.info("【放款中心】收到放款申请: {}", event.getOrderNo());

        // 1. 幂等校验：必须是 APPROVED 才能放款
        LoanOrder order = orderRepository.findByOrderNo(event.getOrderNo());
        if (order == null || order.getStatus() != OrderStatus.APPROVED) {
            log.warn("订单 {} 状态不是 APPROVED，放弃放款", event.getOrderNo());
            return;
        }

        // 2. 推进状态到 PAYING（防止并发重复放款）
        int rows = orderRepository.updateStatusByOrderNo(
                event.getOrderNo(),
                OrderStatus.APPROVED,
                OrderStatus.PAYING
        );
        if (rows <= 0) {
            log.warn("订单 {} 状态更新为 PAYING 失败，可能已被处理", event.getOrderNo());
            return;
        }

        // 3. 模拟调用银行网关
        log.info("【放款中心】正在通过银联拨付资金至用户账户...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            log.error("放款中断", e);
        }

        // 4. 最终流转到 SUCCESS
        orderRepository.updateStatusByOrderNo(event.getOrderNo(), OrderStatus.PAYING, OrderStatus.SUCCESS);
        log.info("【放款中心】订单 {} 放款成功！已通知用户短信查收", event.getOrderNo());
    }
}
