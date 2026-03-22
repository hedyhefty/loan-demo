package com.loan.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.app.config.RabbitConfig;
import com.loan.app.dto.LoanApplyDTO;
import com.loan.app.event.LoanOrderCreatedEvent;
import com.loan.app.exception.BizException;
import com.loan.app.outbox.OutboxScheduler;
import com.loan.infra.common.aop.Idempotent;
import com.loan.domain.order.entity.LoanOrder;
import com.loan.domain.order.repository.LoanOrderRepository;
import com.loan.domain.outbox.entity.OutboxMessage;
import com.loan.domain.outbox.repository.OutboxRepository;
import com.loan.infra.common.redis.RedisLimitManager;
import io.micrometer.tracing.Tracer;
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
    private final Tracer tracer;

    @Transactional(rollbackFor = Exception.class)
    @Idempotent(key = "#dto.orderNo", prefix = "idempotent:apply:", expire = 300)
    public String applyLoan(LoanApplyDTO dto) {
        // 获取当前 traceId 用于全链路追踪
        String traceId = tracer.currentSpan().context().traceId();
        log.info("收到借款申请: 用户={}, 金额={}, 单号={}, traceId={}",
                dto.getUserId(), dto.getAmount(), dto.getOrderNo(), traceId);

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

            // 注册事务同步回调（在 save 之前注册，确保所有分支都能触发）
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // nothing
                }

                @Override
                public void afterCompletion(int status) {
                    // 只有事务回滚时才释放 Redis 额度（正常提交不释放）
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        log.info("事务回滚，释放 Redis 额度: userId={}", dto.getUserId());
                        redisLimitManager.releaseLimit(dto.getUserId(), amount);
                    }
                }
            });

            // 3. 持久化到 MySQL（幂等：依靠 order_no UNIQUE KEY）
            orderRepository.save(order);

            // 4. 组装事件消息
            LoanOrderCreatedEvent event = new LoanOrderCreatedEvent(
                    order.getOrderNo(),
                    dto.getUserId(),
                    BigDecimal.valueOf(dto.getAmount())
            );

            // 5. 将消息存入本地消息表（与订单在同一事务），同时保存 traceId
            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .messageId(order.getOrderNo())
                    .payload(toJson(event))
                    .routeKey(RabbitConfig.ROUTING_KEY)
                    .status(OutboxMessage.STATUS_PENDING)
                    .traceId(traceId)
                    .build();

            // 6. 事务提交后：快速通道尝试立即发送MQ（失败由调度器补偿）
            // 注意：afterCommit 在事务成功提交后执行，此时 afterCompletion 已注册不会执行
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    outboxScheduler.trySendImmediately(outboxMessage);
                }
            });
            outboxRepository.save(outboxMessage);

            log.info("借款订单创建成功: {}", dto.getOrderNo());
            return dto.getOrderNo();

        } catch (DuplicateKeyException e) {
            // 幂等处理：重复请求直接返回
            throw new BizException(500, "订单重复");
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
