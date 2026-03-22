package com.loan.domain.outbox.repository;

import com.loan.domain.outbox.entity.OutboxMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository {

    void save(OutboxMessage message);

    void updateStatus(Long id, String status);

    void updateStatusWithRetry(Long id, LocalDateTime nextRetryTime);

    List<OutboxMessage> selectPendingMessages(LocalDateTime threshold, int limit);
}