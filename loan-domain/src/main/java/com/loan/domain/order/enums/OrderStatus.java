package com.loan.domain.order.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    INIT(10, "初始化"),
    APPROVING(20, "审核中"),
    PAYING(30, "放款中"),
    SUCCESS(40, "放款成功"),
    FAILED(50, "放款失败"),
    CANCELLED(60, "已取消");

    private final int code;
    private final String desc;

    /**
     * 简单的状态机逻辑：判断当前状态是否可以流转到目标状态
     */
    public boolean canTransitionTo(OrderStatus target) {
        // 例如：只有初始化或审核中才能去放款
        return switch (this) {
            case INIT -> target == APPROVING || target == CANCELLED;
            case APPROVING -> target == PAYING || target == FAILED;
            case PAYING -> target == SUCCESS || target == FAILED;
            default -> false; // 终态（SUCCESS/FAILED/CANCELLED）不可再转
        };
    }
}