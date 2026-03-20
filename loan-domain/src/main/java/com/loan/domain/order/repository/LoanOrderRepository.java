package com.loan.domain.order.repository;

import com.loan.domain.order.entity.LoanOrder;

public interface LoanOrderRepository {
    void save(LoanOrder order);

    LoanOrder findByOrderNo(String orderNo);

    void updateStatus(LoanOrder order);
}