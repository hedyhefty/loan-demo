package com.loan.infra.order.converter;

import com.loan.domain.order.entity.LoanOrder;
import com.loan.infra.order.po.LoanOrderPO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderConverter {
    // 实体 -> PO
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    LoanOrderPO toPO(LoanOrder domain);

    // PO -> 实体
    LoanOrder toDomain(LoanOrderPO po);
}
