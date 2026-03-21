package com.loan.domain.order.entity;

import com.loan.domain.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanOrder {
    private Long id;
    private String orderNo;     // 业务订单号
    private Long userId;        // 用户ID
    private BigDecimal amount;  // 申请金额
    private OrderStatus status; // 当前状态

    // 审计字段
    private LocalDateTime createTime;
    private Long version;       // 乐观锁版本号，防止并发写冲突

    /**
     * 核心业务行为：申请放款
     */
    public void apply() {
        if (this.status != OrderStatus.INIT) {
            throw new IllegalStateException("订单状态异常，无法发起申请");
        }
        // 变更状态
        transitionTo(OrderStatus.APPROVING);
    }

    /**
     * 状态流转守卫
     */
    private void transitionTo(OrderStatus targetStatus) {
        if (!this.status.canTransitionTo(targetStatus)) {
            throw new RuntimeException(String.format("非法状态流转: %s -> %s", this.status, targetStatus));
        }
        this.status = targetStatus;
    }

    /**
     * 静态工厂方法：创建借款订单
     */
    public static LoanOrder create(Long userId, Double amount, String orderNo) {
        return LoanOrder.builder()
                .orderNo(orderNo)
                .userId(userId)
                .amount(BigDecimal.valueOf(amount))
                .status(OrderStatus.INIT)
                .version(0L)
                .build();
    }

    /**
     * 这种写法叫”充血模型”，业务逻辑跟着数据走，
     * 而不是在 Service 里写一堆 if-else。
     */
}