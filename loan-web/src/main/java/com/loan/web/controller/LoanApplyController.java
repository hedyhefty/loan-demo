package com.loan.web.controller;

import com.loan.app.dto.LoanApplyDTO;
import com.loan.app.service.LoanApplicationService;
import com.loan.web.common.Result;
import com.loan.web.vo.LoanApplyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loan")
@RequiredArgsConstructor
public class LoanApplyController {

    private final LoanApplicationService loanApplicationService;

    @PostMapping("/apply")
    public Result<LoanApplyVO> apply(@RequestBody LoanApplyDTO dto) {
        String orderNo = loanApplicationService.applyLoan(dto);

        LoanApplyVO vo = LoanApplyVO.builder()
                .userId(dto.getUserId())
                .amount(dto.getAmount())
                .orderNo(orderNo)
                .status("INIT")
                .build();

        return Result.success(vo);
    }
}
