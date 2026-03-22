package com.loan.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.app.config.RabbitConfig;
import com.loan.app.dto.LoanApplyDTO;
import com.loan.app.event.LoanOrderCreatedEvent;
import com.loan.app.exception.BizException;
import com.loan.app.outbox.OutboxScheduler;
import com.loan.domain.order.entity.LoanOrder;
import com.loan.domain.order.repository.LoanOrderRepository;
import com.loan.domain.outbox.entity.OutboxMessage;
import com.loan.domain.outbox.repository.OutboxRepository;
import com.loan.infra.common.redis.RedisLimitManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanOrderRepository orderRepository;
    private final RedisLimitManager redisLimitManager;
    private final OutboxRepository outboxRepository;
    private final OutboxScheduler outboxScheduler;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public String applyLoan(LoanApplyDTO dto) {
        log.info("收到借款申请: 用户={}, 金额={}, 单号={}", dto.getUserId(), dto.getAmount(), dto.getOrderNo());

        BigDecimal amount = BigDecimal.valueOf(dto.getAmount());

        // 1. Redis Lua 预控（内部自动从DB懒加载用户额度）
        boolean acquired = redisLimitManager.tryAcquireLimit(dto.getUserId(), amount);

        if (!acquired) {
            log.warn("用户额度不足: userId={}", dto.getUserId());
            throw new BizException("申请失败：可用额度不足");
        }

        try {
            // 2. 构造 Domain 实体
            LoanOrder order = LoanOrder.create(
                    dto.getUserId(),
                    dto.getAmount(),
                    dto.getOrderNo()
            );

            // 3. 持久化到 MySQL（幂等：依靠 order_no UNIQUE KEY）
            orderRepository.save(order);

            // 4. 组装事件消息
            LoanOrderCreatedEvent event = new LoanOrderCreatedEvent(
                    order.getOrderNo(),
                    dto.getUserId(),
                    BigDecimal.valueOf(dto.getAmount())
            );

            // 5. 将消息存入本地消息表（与订单在同一事务）
            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .messageId(order.getOrderNo())
                    .payload(toJson(event))
                    .routeKey(RabbitConfig.ROUTING_KEY)
                    .status(OutboxMessage.STATUS_PENDING)
                    .build();
            outboxRepository.save(outboxMessage);

            // 6. 事务提交后：快速通道尝试立即发送MQ（失败由调度器补偿）
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    outboxScheduler.trySendImmediately(outboxMessage);
                }

                @Override
                public void afterCompletion(int status) {
                    // 只有事务确定回滚时才释放Redis额度，避免快速通道还在发MQ时误释放
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        log.info("事务已回滚，准备释放 Redis 额度: userId={}", dto.getUserId());
                        redisLimitManager.releaseLimit(dto.getUserId(), amount);
                    }
                }
            });

            log.info("借款订单创建成功: {}", dto.getOrderNo());
            return dto.getOrderNo();

        } catch (DuplicateKeyException e) {
            // 幂等处理：重复请求直接返回
            log.warn("检测到重复提交订单，触发幂等返回: {}", dto.getOrderNo());
            return dto.getOrderNo();

        } catch (Exception e) {
            // 这里不再手动释放额度，而是依赖 afterCompletion 的精确释放
            log.error("系统异常导致订单写入失败: orderNo={}", dto.getOrderNo(), e);
            throw new BizException(500, "系统繁忙，请稍后再试");
        }
    }

    private String toJson(LoanOrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "消息序列化失败");
        }
    }
}
