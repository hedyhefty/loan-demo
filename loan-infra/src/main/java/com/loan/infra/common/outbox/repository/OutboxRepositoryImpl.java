package com.loan.infra.common.outbox.repository;

import com.loan.domain.outbox.entity.OutboxMessage;
import com.loan.domain.outbox.repository.OutboxRepository;
import com.loan.infra.common.outbox.converter.OutboxConverter;
import com.loan.infra.common.outbox.mapper.OutboxMapper;
import com.loan.infra.common.outbox.po.OutboxMessagePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxMapper outboxMapper;
    private final OutboxConverter outboxConverter;

    @Override
    public void save(OutboxMessage message) {
        OutboxMessagePO po = new OutboxMessagePO();
        po.setMessageId(message.getMessageId());
        po.setPayload(message.getPayload());
        po.setRouteKey(message.getRouteKey());
        po.setStatus(message.getStatus());
        po.setRetryCount(0);
        outboxMapper.insert(po);
    }

    @Override
    public void updateStatus(Long id, String status) {
        outboxMapper.updateStatus(id, status);
    }

    @Override
    public void updateStatusWithRetry(Long id, LocalDateTime nextRetryTime) {
        outboxMapper.updateStatusWithRetry(id, nextRetryTime);
    }

    @Override
    public List<OutboxMessage> selectPendingMessages(LocalDateTime threshold, int limit) {
        List<OutboxMessagePO> pos = outboxMapper.selectPendingMessages(threshold, limit);
        return outboxConverter.toEntityList(pos);
    }
}