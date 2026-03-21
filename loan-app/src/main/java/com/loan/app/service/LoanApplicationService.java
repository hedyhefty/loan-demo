package com.loan.app.service;

import com.loan.app.dto.LoanApplyDTO;
import com.loan.app.exception.BizException;
import com.loan.domain.order.entity.LoanOrder;
import com.loan.domain.order.repository.LoanOrderRepository;
import com.loan.infra.common.redis.RedisLimitManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanOrderRepository orderRepository;
    private final RedisLimitManager redisLimitManager;

    private static final double DEFAULT_MAX_LIMIT = 5000.0;

    @Transactional(rollbackFor = Exception.class)
    public String applyLoan(LoanApplyDTO dto) {
        log.info("收到借款申请: 用户={}, 金额={}, 单号={}", dto.getUserId(), dto.getAmount(), dto.getOrderNo());

        // 1. Redis Lua 预控（防止高并发超支）
        boolean acquired = redisLimitManager.tryAcquireLimit(
                dto.getUserId(),
                dto.getAmount(),
                DEFAULT_MAX_LIMIT
        );

        if (!acquired) {
            log.warn("用户额度不足: userId={}", dto.getUserId());
            throw new BizException("申请失败：可用额度不足");
        }

        try {
            // 2. 构造 Domain 实体
            LoanOrder order = LoanOrder.create(
                    dto.getUserId(),
                    dto.getAmount(),
                    dto.getOrderNo()
            );

            // 3. 持久化到 MySQL（幂等：依靠 order_no UNIQUE KEY）
            orderRepository.save(order);

            log.info("借款订单创建成功: {}", dto.getOrderNo());
            return dto.getOrderNo();

        } catch (DuplicateKeyException e) {
            // 4. 幂等处理：重复请求直接返回
            log.warn("检测到重复提交订单，触发幂等返回: {}", dto.getOrderNo());
            return dto.getOrderNo();

        } catch (Exception e) {
            // 5. 异常补偿：释放 Redis 额度
            log.error("系统异常导致订单写入失败，执行 Redis 额度释放: {}", dto.getOrderNo());
            redisLimitManager.releaseLimit(dto.getUserId(), dto.getAmount());
            throw new BizException(500, "系统繁忙，请稍后再试");
        }
    }
}
