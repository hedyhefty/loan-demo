package com.loan.infra.user.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.domain.user.repository.UserLimitRepository;
import com.loan.infra.user.mapper.UserLimitMapper;
import com.loan.infra.user.po.UserLimitPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserLimitRepositoryImpl implements UserLimitRepository {

    private final UserLimitMapper userLimitMapper;

    @Override
    public BigDecimal getAvailableLimit(Long userId) {
        UserLimitPO po = userLimitMapper.selectOne(
                new LambdaQueryWrapper<UserLimitPO>()
                        .eq(UserLimitPO::getUserId, userId)
        );
        return po != null ? po.getAvailableLimit() : null;
    }

    @Override
    public int decreaseLimit(Long userId, BigDecimal amount) {
        return userLimitMapper.secureDecreaseLimit(userId, amount);
    }

    @Override
    public void restoreLimit(Long userId, BigDecimal amount) {
        // 恢复 MySQL 可用额度（用于放款失败等场景回滚真实扣减）
        userLimitMapper.restoreLimit(userId, amount);
    }

    @Override
    public List<Long> findAllActiveUserIds() {
        LambdaQueryWrapper<UserLimitPO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserLimitPO::getStatus, 1)  // 1=正常, 0=冻结
                .gt(UserLimitPO::getAvailableLimit, BigDecimal.ZERO)
                .select(UserLimitPO::getUserId);
        return userLimitMapper.selectList(queryWrapper)
                .stream()
                .map(UserLimitPO::getUserId)
                .toList();
    }
}
