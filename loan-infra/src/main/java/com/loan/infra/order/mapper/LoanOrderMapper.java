package com.loan.infra.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.infra.order.po.LoanOrderPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoanOrderMapper extends BaseMapper<LoanOrderPO> {
    // 这里空着就行，你已经拥有了：
    // insert, deleteById, updateById, selectById, selectList 等几十个方法
}