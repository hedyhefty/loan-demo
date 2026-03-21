package com.loan.app.consumer;

import com.loan.app.config.RabbitConfig;
import com.loan.app.event.LoanOrderCreatedEvent;
import com.loan.domain.order.enums.OrderStatus;
import com.loan.domain.order.repository.LoanOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanAuditConsumer {

    private final LoanOrderRepository orderRepository;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void handleOrderCreated(LoanOrderCreatedEvent event) {
        log.info("【风控系统】开始审批订单: {}", event.getOrderNo());

        // 1. 模拟风控计算
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            log.error("睡眠被打断", e);
        }

        // 2. 决策逻辑
        boolean approved = event.getAmount() <= 3000;
        OrderStatus targetStatus = approved ? OrderStatus.APPROVED : OrderStatus.REJECTED;

        // 3. 执行数据库更新（带前置状态校验）
        int rows = orderRepository.updateStatusByOrderNo(
                event.getOrderNo(),
                OrderStatus.INIT,
                targetStatus
        );

        if (rows > 0) {
            log.info("【风控系统】订单 {} 状态已更新为: {} ✅", event.getOrderNo(), targetStatus);
        } else {
            log.warn("【风控系统】订单 {} 状态更新失败，可能已被处理", event.getOrderNo());
        }
    }
}
