package com.loan.domain.outbox.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessage {
    private Long id;
    private String messageId;
    private String payload;
    private String routeKey;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
}