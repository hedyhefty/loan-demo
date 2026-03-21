package com.loan.web.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoanApplyVO {
    private Long userId;
    private Double amount;
    private String orderNo;
    private String status;
}
