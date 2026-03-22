-- 本地消息表 (Transactional Outbox)
CREATE TABLE `t_message_outbox` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `message_id` VARCHAR(64) NOT NULL COMMENT '唯一消息ID',
    `payload` TEXT NOT NULL COMMENT '消息正文内容JSON',
    `route_key` VARCHAR(64) NOT NULL COMMENT '交换机/路由键',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING, SENT, FAILED',
    `retry_count` INT DEFAULT 0,
    `next_retry_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_id` (`message_id`),
    KEY `idx_status_next_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;