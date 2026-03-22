-- 本地消息表 (Transactional Outbox)
CREATE TABLE `t_message_outbox` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `message_id` VARCHAR(64) NOT NULL COMMENT '唯一消息ID',
    `payload` TEXT NOT NULL COMMENT '消息正文内容JSON',
    `route_key` VARCHAR(64) NOT NULL COMMENT '交换机/路由键',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING, SENT, FAILED',
    `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '全链路追踪ID',
    `retry_count` INT DEFAULT 0,
    `next_retry_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_id` (`message_id`),
    KEY `idx_status_next_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 借款订单表
CREATE TABLE `t_loan_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '业务订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `amount` DECIMAL(18,2) NOT NULL COMMENT '借款金额',
    `status` VARCHAR(20) NOT NULL COMMENT '订单状态',
    `version` BIGINT NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_status_update_time` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借款订单表';

-- 如果表已存在，添加 trace_id 列
-- ALTER TABLE `t_message_outbox` ADD COLUMN `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '全链路追踪ID' AFTER `status`;

-- 如果表已存在，添加对账所需的复合索引
-- ALTER TABLE `t_loan_order` ADD KEY `idx_status_update_time` (`status`, `update_time`);