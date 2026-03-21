package com.loan.app.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class LoanApplyDTO implements Serializable {
    private Long userId;
    private Double amount;
    private String orderNo;
}
