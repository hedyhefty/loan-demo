package com.loan.infra.order.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_loan_order")
public class LoanOrderPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    private String status; // 数据库存字符串或数字，映射枚举

    @Version // MyBatis-Plus 乐观锁注解
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}