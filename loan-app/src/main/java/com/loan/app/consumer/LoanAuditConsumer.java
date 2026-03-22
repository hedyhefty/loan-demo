package com.loan.app.consumer;

import com.loan.app.config.RabbitConfig;
import com.loan.app.event.LoanOrderCreatedEvent;
import com.loan.domain.order.enums.OrderStatus;
import com.loan.domain.order.repository.LoanOrderRepository;
import com.loan.domain.user.repository.UserLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanAuditConsumer {

    private final LoanOrderRepository orderRepository;
    private final UserLimitRepository userLimitRepository;
    private final RabbitTemplate rabbitTemplate;

    private static final BigDecimal APPROVAL_THRESHOLD = BigDecimal.valueOf(3000);

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
        boolean approved = event.getAmount().compareTo(APPROVAL_THRESHOLD) <= 0;

        if (approved) {
            // 3. 实扣数据库额度
            int rows = userLimitRepository.decreaseLimit(event.getUserId(), event.getAmount());
            if (rows <= 0) {
                log.error("【资产异常】订单 {} 额度扣减失败，可用额度不足！", event.getOrderNo());
                orderRepository.updateStatusByOrderNo(event.getOrderNo(), OrderStatus.INIT, OrderStatus.REJECTED);
                userLimitRepository.restoreLimit(event.getUserId(), event.getAmount());
                return;
            }

            // 4. 更新订单状态为 APPROVED
            orderRepository.updateStatusByOrderNo(event.getOrderNo(), OrderStatus.INIT, OrderStatus.APPROVED);
            log.info("【资产安全】订单 {} 额度实扣成功，状态已更新为 APPROVED", event.getOrderNo());

            // 5. 发送放款消息（事务提交后）
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("审批事务提交，发送放款指令: {}", event.getOrderNo());
                    rabbitTemplate.convertAndSend(RabbitConfig.FUNDING_EXCHANGE, RabbitConfig.FUNDING_ROUTING_KEY, event);
                }
            });
        } else {
            // 6. 审批拒绝，回滚 Redis 预扣额度
            log.info("【风控系统】订单 {} 审批拒绝，回滚 Redis 额度", event.getOrderNo());
            userLimitRepository.restoreLimit(event.getUserId(), event.getAmount());
            orderRepository.updateStatusByOrderNo(event.getOrderNo(), OrderStatus.INIT, OrderStatus.REJECTED);
            log.info("【风控系统】订单 {} 状态已更新为 REJECTED", event.getOrderNo());
        }
    }
}
