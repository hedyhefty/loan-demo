package com.loan.infra.user.repository;

import com.loan.domain.user.repository.UserLimitRepository;
import com.loan.infra.common.redis.RedisLimitManager;
import com.loan.infra.user.mapper.UserLimitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserLimitRepositoryImpl implements UserLimitRepository {

    private final UserLimitMapper userLimitMapper;
    private final RedisLimitManager redisLimitManager;

    @Override
    public int decreaseLimit(Long userId, Double amount) {
        return userLimitMapper.secureDecreaseLimit(userId, amount);
    }

    @Override
    public void restoreLimit(Long userId, Double amount) {
        redisLimitManager.releaseLimit(userId, amount);
    }
}
