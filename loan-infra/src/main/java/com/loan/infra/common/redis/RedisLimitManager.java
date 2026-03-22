package com.loan.infra.common.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.util.Collections;

@Component
public class RedisLimitManager {

    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> limitScript;
    private DefaultRedisScript<Long> releaseScript;

    public RedisLimitManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        limitScript = new DefaultRedisScript<>();
        limitScript.setResultType(Long.class);
        limitScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/limit_check.lua")));

        releaseScript = new DefaultRedisScript<>();
        releaseScript.setResultType(Long.class);
        releaseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/limit_release.lua")));
    }

    public boolean tryAcquireLimit(Long userId, BigDecimal amount, BigDecimal maxLimit) {
        String key = "loan:limit:user:" + userId;
        Long result = redisTemplate.execute(
                limitScript,
                Collections.singletonList(key),
                amount.toPlainString(),
                maxLimit.toPlainString()
        );
        return result != null && result == 1;
    }

    public void releaseLimit(Long userId, BigDecimal amount) {
        String key = "loan:limit:user:" + userId;
        redisTemplate.execute(
                releaseScript,
                Collections.singletonList(key),
                amount.toPlainString()
        );
    }
}