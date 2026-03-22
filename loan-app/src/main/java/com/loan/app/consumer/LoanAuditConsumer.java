package com.loan.app.consumer;

import com.loan.app.config.RabbitConfig;
import com.loan.app.event.LoanOrderCreatedEvent;
import com.loan.domain.order.enums.OrderStatus;
import com.loan.domain.order.repository.LoanOrderRepository;
import com.loan.domain.user.repository.UserLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanAuditConsumer {

    private final LoanOrderRepository orderRepository;
    private final UserLimitRepository userLimitRepository;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    @Transactional
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

        if (approved) {
            // 3. 实扣数据库额度
            int rows = userLimitRepository.decreaseLimit(event.getUserId(), event.getAmount());
            if (rows <= 0) {
                log.error("【资产异常】订单 {} 额度扣减失败，可用额度不足！", event.getOrderNo());
                // 额度不足，更新订单为拒绝状态
                orderRepository.updateStatusByOrderNo(event.getOrderNo(), OrderStatus.INIT, OrderStatus.REJECTED);
                // 回滚 Redis 预扣额度
                userLimitRepository.restoreLimit(event.getUserId(), event.getAmount());
                return;
            }

            // 4. 更新订单状态为 APPROVED
            orderRepository.updateStatusByOrderNo(event.getOrderNo(), OrderStatus.INIT, OrderStatus.APPROVED);
            log.info("【资产安全】订单 {} 额度实扣成功，状态已更新为 APPROVED", event.getOrderNo());
        } else {
            // 5. 审批拒绝，回滚 Redis 预扣额度
            log.info("【风控系统】订单 {} 审批拒绝，回滚 Redis 额度", event.getOrderNo());
            userLimitRepository.restoreLimit(event.getUserId(), event.getAmount());
            orderRepository.updateStatusByOrderNo(event.getOrderNo(), OrderStatus.INIT, OrderStatus.REJECTED);
            log.info("【风控系统】订单 {} 状态已更新为 REJECTED", event.getOrderNo());
        }
    }
}
