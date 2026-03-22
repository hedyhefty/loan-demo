package com.loan.app.service;

import com.loan.app.config.RabbitConfig;
import com.loan.app.outbox.OutboxScheduler;
import com.loan.domain.order.entity.LoanOrder;
import com.loan.domain.order.enums.OrderStatus;
import com.loan.domain.order.repository.LoanOrderRepository;
import com.loan.domain.outbox.entity.OutboxMessage;
import com.loan.domain.outbox.repository.OutboxRepository;
import com.loan.infra.common.redis.RedisLockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账服务：修复因网络抖动、进程崩溃、第三方不可用导致的"中间态"订单。
 *
 * <p>包含两个对账任务：
 * <ol>
 *   <li>Outbox 消息补齐：确保每条消息都进入 MQ</li>
 *   <li>业务状态对账：确保卡在中间态的订单达到终态</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanReconciliationService {

    private static final String LOCK_OUTBOX = "lock:reconcile:outbox";
    private static final String LOCK_BUSINESS = "lock:reconcile:business";
    private static final Duration LOCK_EXPIRE = Duration.ofSeconds(55);

    private static final int BATCH_SIZE = 100;
    private static final int STUCK_THRESHOLD_MINUTES = 5;
    private static final int OUTBOX_THRESHOLD_MINUTES = 1;

    private final OutboxRepository outboxRepository;
    private final LoanOrderRepository orderRepository;
    private final OutboxScheduler outboxScheduler;
    private final RabbitTemplate rabbitTemplate;
    private final RedisLockManager redisLockManager;

    // ==================== 任务一：Outbox 消息补齐 ====================

    /**
     * 扫描并重试卡在 PENDING 状态的 outbox 消息。
     * <p>扫描 1 分钟前创建的消息，避免与正在执行 afterCommit 的正常流程冲突。
     * 每分钟执行一次。
     */
    @Scheduled(fixedDelay = 60000)
    public void outboxReconciliation() {
        if (!redisLockManager.tryLock(LOCK_OUTBOX, LOCK_EXPIRE)) {
            log.debug("【对账-Outbox】未获取到锁，跳过本次执行");
            return;
        }

        try {
            doOutboxReconciliation();
        } finally {
            redisLockManager.unlock(LOCK_OUTBOX);
        }
    }

    private void doOutboxReconciliation() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(OUTBOX_THRESHOLD_MINUTES);
        List<OutboxMessage> pending = outboxRepository.selectPendingMessages(threshold, BATCH_SIZE);

        if (pending.isEmpty()) {
            return;
        }

        log.info("【对账-Outbox】扫描到 {} 条积压消息", pending.size());

        for (OutboxMessage msg : pending) {
            try {
                outboxScheduler.trySendImmediately(msg);
            } catch (Exception e) {
                log.error("【对账-Outbox】消息发送失败: messageId={}, traceId={}",
                        msg.getMessageId(), msg.getTraceId(), e);
            }
        }
    }

    // ==================== 任务二：业务状态对账 ====================

    /**
     * 对账卡在中间态的订单，确保达到终态。
     * <p>扫描 5 分钟前未更新的 APPROVED/PAYING 订单，主动查询第三方状态并推进流转。
     * 每 5 分钟执行一次。
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void businessReconciliation() {
        if (!redisLockManager.tryLock(LOCK_BUSINESS, LOCK_EXPIRE)) {
            log.debug("【对账-业务】未获取到锁，跳过本次执行");
            return;
        }

        try {
            doBusinessReconciliation();
        } finally {
            redisLockManager.unlock(LOCK_BUSINESS);
        }
    }

    private void doBusinessReconciliation() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<OrderStatus> stuckStatuses = List.of(OrderStatus.APPROVED, OrderStatus.PAYING);
        List<LoanOrder> stuckOrders = orderRepository.findStuckOrders(stuckStatuses, threshold, BATCH_SIZE);

        if (stuckOrders.isEmpty()) {
            return;
        }

        log.info("【对账-业务】扫描到 {} 条卡住订单", stuckOrders.size());

        for (LoanOrder order : stuckOrders) {
            try {
                reconcileOrder(order);
            } catch (Exception e) {
                log.error("【对账-业务】订单对账异常: orderNo={}", order.getOrderNo(), e);
            }
        }
    }

    /**
     * 对单个卡住订单进行处理：
     * <ul>
     *   <li>APPROVED -> 主动触发放款消息（银行侧可能未收到）</li>
     *   <li>PAYING -> 查询银行状态，推进到 SUCCESS/FAILED</li>
     * </ul>
     */
    private void reconcileOrder(LoanOrder order) {
        log.info("【对账-业务】处理卡住订单: orderNo={}, status={}, updateTime={}",
                order.getOrderNo(), order.getStatus(), order.getUpdateTime());

        if (order.getStatus() == OrderStatus.APPROVED) {
            // 银行未收到放款请求，重新触发放款流程
            log.warn("【对账-业务】订单卡在 APPROVED，重新触发放款: {}", order.getOrderNo());
            triggerFunding(order);

        } else if (order.getStatus() == OrderStatus.PAYING) {
            // 查询银行状态，这里模拟返回 SUCCESS
            ExternalFundingStatus result = queryExternalFundingStatus(order.getOrderNo());

            if (result == ExternalFundingStatus.SUCCESS) {
                int rows = orderRepository.updateStatusByOrderNo(
                        order.getOrderNo(), OrderStatus.PAYING, OrderStatus.SUCCESS);
                if (rows > 0) {
                    log.info("【对账-业务】订单状态已同步为 SUCCESS: {}", order.getOrderNo());
                }
            } else if (result == ExternalFundingStatus.FAILED) {
                int rows = orderRepository.updateStatusByOrderNo(
                        order.getOrderNo(), OrderStatus.PAYING, OrderStatus.FAILED);
                if (rows > 0) {
                    log.warn("【对账-业务】订单状态已回滚为 FAILED: {}", order.getOrderNo());
                }
            }
            // NOT_FOUND 继续等待下次对账
        }
    }

    private void triggerFunding(LoanOrder order) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.FUNDING_EXCHANGE,
                RabbitConfig.FUNDING_ROUTING_KEY,
                new com.loan.app.event.LoanOrderCreatedEvent(
                        order.getOrderNo(),
                        order.getUserId(),
                        order.getAmount()
                )
        );
        log.info("【对账-业务】已重新触发放款消息: orderNo={}", order.getOrderNo());
    }

    /**
     * 模拟查询第三方银行放款状态。
     * 实际项目中应注入真实的 FundingClient。
     */
    private ExternalFundingStatus queryExternalFundingStatus(String orderNo) {
        // 模拟：永远返回 SUCCESS（压测环境下认为银行已处理）
        return ExternalFundingStatus.SUCCESS;
    }

    /**
     * 第三方放款状态枚举
     */
    private enum ExternalFundingStatus {
        SUCCESS,   // 放款成功
        FAILED,    // 放款失败
        NOT_FOUND  // 银行侧未查到记录
    }
}
