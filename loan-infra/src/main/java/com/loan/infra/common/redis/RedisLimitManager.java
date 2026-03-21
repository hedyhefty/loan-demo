package com.loan.infra.common.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.Collections;

@Component
public class RedisLimitManager {

    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> limitScript;

    public RedisLimitManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        limitScript = new DefaultRedisScript<>();
        limitScript.setResultType(Long.class);
        limitScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/limit_check.lua")));
    }

    public boolean tryAcquireLimit(Long userId, double amount, double maxLimit) {
        String key = "loan:limit:user:" + userId;
        // 执行 Lua 脚本
        Long result = redisTemplate.execute(
                limitScript,
                Collections.singletonList(key),
                String.valueOf(amount),
                String.valueOf(maxLimit)
        );
        return result != null && result == 1;
    }
}