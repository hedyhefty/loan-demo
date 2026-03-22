package com.loan.infra.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisLockManager {

    private final StringRedisTemplate redisTemplate;

    public RedisLockManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取分布式锁
     * @param lockKey 锁key
     * @param expire 锁过期时间
     * @return true=获取锁成功，false=获取锁失败
     */
    public boolean tryLock(String lockKey, Duration expire) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", expire);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放分布式锁
     */
    public void unlock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}