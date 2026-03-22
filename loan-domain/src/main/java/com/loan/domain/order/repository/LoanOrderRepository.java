package com.loan.domain.order.repository;

import com.loan.domain.order.entity.LoanOrder;
import com.loan.domain.order.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 查询卡在中间状态的订单（用于业务对账）
     * @param statuses 需要对账的状态列表
     * @param threshold 更新时间早于此时间的订单视为"卡住"
     * @param limit 每批处理数量
     * @return 卡住的订单列表
     */
    List<LoanOrder> findStuckOrders(List<OrderStatus> statuses, LocalDateTime threshold, int limit);
}