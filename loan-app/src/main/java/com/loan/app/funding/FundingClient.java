package com.loan.app.funding;

import com.loan.domain.order.entity.LoanOrder;

/**
 * 放款客户端接口
 * <p>封装对第三方银行/放款平台的调用，支持熔断保护
 */
public interface FundingClient {

    /**
     * 发起放款请求
     * @param order 借款订单
     * @return 放款结果
     */
    FundingResult apply(LoanOrder order);

    /**
     * 查询放款状态
     * @param orderNo 订单号
     * @return 放款结果
     */
    FundingResult queryStatus(String orderNo);

    /**
     * 放款结果枚举
     */
    enum FundingResult {
        SUCCESS,   // 放款成功
        FAILED,    // 放款失败
        PROCESSING // 处理中
    }
}
