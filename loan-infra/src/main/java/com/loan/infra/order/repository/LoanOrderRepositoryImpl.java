package com.loan.infra.order.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loan.domain.order.entity.LoanOrder;
import com.loan.domain.order.enums.OrderStatus;
import com.loan.domain.order.repository.LoanOrderRepository;
import com.loan.infra.order.converter.OrderConverter;
import com.loan.infra.order.mapper.LoanOrderMapper;
import com.loan.infra.order.po.LoanOrderPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class LoanOrderRepositoryImpl implements LoanOrderRepository {

    private final LoanOrderMapper orderMapper;

    private final OrderConverter orderConverter;

    @Override
    public void save(LoanOrder order) {
        LoanOrderPO po = orderConverter.toPO(order);
        orderMapper.insert(po);
    }

    @Override
    public void updateStatus(LoanOrder order) {
        LoanOrderPO po = new LoanOrderPO();
        po.setId(order.getId());
        po.setStatus(order.getStatus().name());
        po.setVersion(order.getVersion());

        int rows = orderMapper.updateById(po);
        if (rows == 0) {
            throw new RuntimeException("并发修改失败，请重试");
        }
    }

    @Override
    public int updateStatusByOrderNo(String orderNo, OrderStatus fromStatus, OrderStatus toStatus) {
        // WHERE条件：订单号 + 当前状态 + 版本号（乐观锁）
        // SET：目标状态 + version = version + 1（数据库原子操作）
        // 只有版本号未被其他事务修改时才能更新成功
        LambdaUpdateWrapper<LoanOrderPO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(LoanOrderPO::getOrderNo, orderNo)
                .eq(LoanOrderPO::getStatus, fromStatus.name())
                .set(LoanOrderPO::getStatus, toStatus.name())
                .setSql("version = version + 1");  // 数据库原子 +1

        return orderMapper.update(null, updateWrapper);
    }

    @Override
    public LoanOrder findByOrderNo(String orderNo) {
        LoanOrderPO po = orderMapper.selectOne(
                new LambdaQueryWrapper<LoanOrderPO>()
                        .eq(LoanOrderPO::getOrderNo, orderNo)
        );

        if (po == null) {
            return null;
        }

        return orderConverter.toDomain(po);
    }

    @Override
    public List<LoanOrder> findStuckOrders(List<OrderStatus> statuses, LocalDateTime threshold, int limit) {
        List<String> statusNames = statuses.stream().map(Enum::name).toList();
        LambdaQueryWrapper<LoanOrderPO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(LoanOrderPO::getStatus, statusNames)
                .lt(LoanOrderPO::getUpdateTime, threshold)
                .orderByAsc(LoanOrderPO::getUpdateTime)
                .last("LIMIT " + limit);

        List<LoanOrderPO> pos = orderMapper.selectList(queryWrapper);
        return pos.stream().map(orderConverter::toDomain).toList();
    }
}