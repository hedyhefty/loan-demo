package com.loan.app.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.app.config.RabbitConfig;
import com.loan.app.event.LoanOrderCreatedEvent;
import com.loan.domain.outbox.entity.OutboxMessage;
import com.loan.domain.outbox.repository.OutboxRepository;
import com.loan.infra.common.redis.RedisLockManager;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RedisLockManager redisLockManager;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    private static final String LOCK_KEY = "lock:outbox:scheduler";
    private static final Duration LOCK_EXPIRE = Duration.ofSeconds(55); // 锁55秒，比调度周期60秒短
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 5;
    private static final int RETRY_INTERVAL_MINUTES = 1;
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 定时扫描并重试待发送消息（补偿机制）
     */
    @Scheduled(fixedDelay = 60000) // 每分钟执行一次
    public void scanAndRetryPendingMessages() {
        // 获取分布式锁，防止多实例惊群效应
        if (!redisLockManager.tryLock(LOCK_KEY, LOCK_EXPIRE)) {
            log.debug("【Outbox调度】未获取到锁，跳过本次执行");
            return;
        }

        try {
            doScanAndRetry();
        } finally {
            redisLockManager.unlock(LOCK_KEY);
        }
    }

    private void doScanAndRetry() {
        LocalDateTime threshold = LocalDateTime.now();
        List<OutboxMessage> pendingMessages = outboxRepository.selectPendingMessages(threshold, BATCH_SIZE);

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info("【Outbox调度】扫描到 {} 条待发送消息", pendingMessages.size());

        for (OutboxMessage message : pendingMessages) {
            try {
                // 恢复 traceId 到 MDC，方便日志追踪
                String originalTraceId = MDC.get("traceId");
                MDC.put("traceId", message.getTraceId());

                try {
                    // 先将JSON字符串反序列化为对象，再发送（让Jackson converter正确处理）
                    LoanOrderCreatedEvent event = objectMapper.readValue(
                            message.getPayload(), LoanOrderCreatedEvent.class);

                    // 发送消息到MQ，同时传递 traceId 到消息头
                    rabbitTemplate.convertAndSend(
                            RabbitConfig.EXCHANGE,
                            message.getRouteKey(),
                            event,
                            msg -> {
                                msg.getMessageProperties().setHeader(TRACE_ID_HEADER, message.getTraceId());
                                return msg;
                            }
                    );

                    // 发送成功，更新状态
                    outboxRepository.updateStatus(message.getId(), OutboxMessage.STATUS_SENT);
                    log.info("【Outbox调度】消息发送成功: messageId={}, traceId={}",
                            message.getMessageId(), message.getTraceId());

                } finally {
                    // 恢复原来的 MDC
                    if (originalTraceId != null) {
                        MDC.put("traceId", originalTraceId);
                    } else {
                        MDC.remove("traceId");
                    }
                }

            } catch (Exception e) {
                log.error("【Outbox调度】消息发送失败: messageId={}, traceId={}, error={}",
                        message.getMessageId(), message.getTraceId(), e.getMessage());

                // 检查重试次数
                if (message.getRetryCount() >= MAX_RETRY_COUNT) {
                    outboxRepository.updateStatus(message.getId(), OutboxMessage.STATUS_FAILED);
                    log.error("【Outbox调度】消息重试次数超限，标记为失败: messageId={}", message.getMessageId());
                } else {
                    LocalDateTime nextRetry = threshold.plusMinutes(RETRY_INTERVAL_MINUTES);
                    outboxRepository.updateStatusWithRetry(message.getId(), nextRetry);
                    log.warn("【Outbox调度】消息将在 {} 重试: messageId={}",
                            nextRetry, message.getMessageId());
                }
            }
        }
    }

    /**
     * 快速通道：事务提交后立即尝试发送消息
     * 如果发送失败，不更新状态（由调度器补偿）
     */
    public void trySendImmediately(OutboxMessage message) {
        try {
            // 恢复 traceId 到 MDC
            String originalTraceId = MDC.get("traceId");
            MDC.put("traceId", message.getTraceId());

            try {
                // 先将JSON字符串反序列化为对象，再发送
                LoanOrderCreatedEvent event = objectMapper.readValue(
                        message.getPayload(), LoanOrderCreatedEvent.class);

                // 发送消息到MQ，同时传递 traceId
                rabbitTemplate.convertAndSend(
                        RabbitConfig.EXCHANGE,
                        message.getRouteKey(),
                        event,
                        msg -> {
                            msg.getMessageProperties().setHeader(TRACE_ID_HEADER, message.getTraceId());
                            return msg;
                        }
                );
                outboxRepository.updateStatus(message.getId(), OutboxMessage.STATUS_SENT);
                log.info("【Outbox快速通道】消息发送成功: messageId={}, traceId={}",
                        message.getMessageId(), message.getTraceId());
            } finally {
                // 恢复原来的 MDC
                if (originalTraceId != null) {
                    MDC.put("traceId", originalTraceId);
                } else {
                    MDC.remove("traceId");
                }
            }
        } catch (Exception e) {
            log.warn("【Outbox快速通道】消息发送失败，等待调度器补偿: messageId={}, traceId={}",
                    message.getMessageId(), message.getTraceId());
            // 不更新状态，调度器会处理
        }
    }
}