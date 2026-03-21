package com.loan.infra.order.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.domain.order.entity.LoanOrder;
import com.loan.domain.order.enums.OrderStatus;
import com.loan.domain.order.repository.LoanOrderRepository;
import com.loan.infra.order.converter.OrderConverter;
import com.loan.infra.order.mapper.LoanOrderMapper;
import com.loan.infra.order.po.LoanOrderPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LoanOrderRepositoryImpl implements LoanOrderRepository {

    private final LoanOrderMapper orderMapper;

    private final OrderConverter orderConverter;

    @Override
    public void save(LoanOrder order) {
        // 1. 将 Domain 实体转为 PO (手动或用 MapStruct)
        LoanOrderPO po = orderConverter.toPO(order);

        // 2. 执行插入
        orderMapper.insert(po);
    }

    @Override
    public void updateStatus(LoanOrder order) {
        LoanOrderPO po = new LoanOrderPO();
        po.setId(order.getId());
        po.setStatus(order.getStatus().name());
        po.setVersion(order.getVersion());

        // 3. MyBatis-Plus 会自动根据 @Version 字段生成：
        // UPDATE t_loan_order SET status = ?, version = version + 1
        // WHERE id = ? AND version = ?
        int rows = orderMapper.updateById(po);
        if (rows == 0) {
            throw new RuntimeException("并发修改失败，请重试");
        }
    }

    @Override
    public LoanOrder findByOrderNo(String orderNo) {
        // 1. 使用 MyBatis-Plus 的 Lambda 查询，避免硬编码字段名
        LoanOrderPO po = orderMapper.selectOne(
                new LambdaQueryWrapper<LoanOrderPO>()
                        .eq(LoanOrderPO::getOrderNo, orderNo)
        );

        if (po == null) {
            return null;
        }

        // 2. 将 PO 还原为 Domain 实体（手动转换示例）
        // 在实际大型项目中，这里建议使用 MapStruct 自动生成转换代码
        return orderConverter.toDomain(po);
    }
}