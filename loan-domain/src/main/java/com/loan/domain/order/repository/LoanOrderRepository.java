package com.loan.domain.order.repository;

import com.loan.domain.order.entity.LoanOrder;
import com.loan.domain.order.enums.OrderStatus;

public interface LoanOrderRepository {
    void save(LoanOrder order);

    LoanOrder findByOrderNo(String orderNo);

    void updateStatus(LoanOrder order);

    /**
     * 根据订单号更新状态（带前置状态校验）
     * @param orderNo 订单号
     * @param fromStatus 前置状态（只有当前状态等于此值时才更新）
     * @param toStatus 目标状态
     * @return 更新行数
     */
    int updateStatusByOrderNo(String orderNo, OrderStatus fromStatus, OrderStatus toStatus);
}