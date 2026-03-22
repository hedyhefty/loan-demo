package com.loan.infra.common.outbox.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_message_outbox")
public class OutboxMessagePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String payload;
    private String routeKey;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}