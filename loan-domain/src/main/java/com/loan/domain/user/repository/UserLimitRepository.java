package com.loan.domain.user.repository;

import java.math.BigDecimal;
import java.util.List;

public interface UserLimitRepository {
    /**
     * 查询用户可用额度
     * @param userId 用户ID
     * @return 可用额度，如果用户不存在返回null
     */
    BigDecimal getAvailableLimit(Long userId);

    /**
     * 原子扣减用户额度
     * @param userId 用户ID
     * @param amount 扣减金额
     * @return 扣减成功的行数，0表示余额不足
     */
    int decreaseLimit(Long userId, BigDecimal amount);

    /**
     * 恢复用户额度（用于订单被拒绝时回滚Redis预扣）
     * @param userId 用户ID
     * @param amount 恢复金额
     */
    void restoreLimit(Long userId, BigDecimal amount);

    /**
     * 查询所有活跃用户ID（用于缓存预热）
     * @return 活跃用户ID列表
     */
    List<Long> findAllActiveUserIds();
}
