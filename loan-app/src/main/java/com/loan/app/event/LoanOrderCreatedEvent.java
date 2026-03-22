package com.loan.app.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoanOrderCreatedEvent implements Serializable {
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
}
