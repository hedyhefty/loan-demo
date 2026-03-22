package com.loan.infra.user.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_user_limit")
public class UserLimitPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private BigDecimal totalLimit;
    private BigDecimal availableLimit;
    private BigDecimal usedLimit;
    private BigDecimal frozenLimit;
    private Integer status;
    @Version
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
