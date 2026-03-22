package com.loan.infra.common.outbox.converter;

import com.loan.domain.outbox.entity.OutboxMessage;
import com.loan.infra.common.outbox.po.OutboxMessagePO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OutboxConverter {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "messageId", source = "messageId")
    @Mapping(target = "payload", source = "payload")
    @Mapping(target = "routeKey", source = "routeKey")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "traceId", source = "traceId")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "updateTime", source = "updateTime")
    @Mapping(target = "retryCount", source = "retryCount")
    @Mapping(target = "nextRetryTime", source = "nextRetryTime")
    OutboxMessage toEntity(OutboxMessagePO po);

    List<OutboxMessage> toEntityList(List<OutboxMessagePO> pos);
}