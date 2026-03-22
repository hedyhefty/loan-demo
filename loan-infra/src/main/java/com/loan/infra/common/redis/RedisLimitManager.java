package com.loan.infra.common.redis;

import com.loan.domain.user.repository.UserLimitRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;

@Component
public class RedisLimitManager {

    private final StringRedisTemplate redisTemplate;
    private final UserLimitRepository userLimitRepository;
    private DefaultRedisScript<Long> limitScript;
    private DefaultRedisScript<Long> releaseScript;

    private static final String LIMIT_KEY_PREFIX = "loan:limit:user:";
    private static final Duration CACHE_EXPIRE = Duration.ofHours(24);

    public RedisLimitManager(StringRedisTemplate redisTemplate,
                            @Lazy UserLimitRepository userLimitRepository) {
        this.redisTemplate = redisTemplate;
        this.userLimitRepository = userLimitRepository;
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

    /**
     * 尝试预扣用户额度（自动从DB懒加载额度到Redis）
     * @param userId 用户ID
     * @param amount 申请金额
     * @return true=预扣成功，false=额度不足
     */
    public boolean tryAcquireLimit(Long userId, BigDecimal amount) {
        String key = LIMIT_KEY_PREFIX + userId;

        // 1. 如果Redis里没这个Key，先从DB加载
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            loadLimitFromDb(userId);
        }

        // 2. 执行Lua脚本进行原子扣减（脚本只接收 key 和 amount）
        Long result = redisTemplate.execute(
                limitScript,
                Collections.singletonList(key),
                amount.toPlainString()
        );
        return result != null && result == 1;
    }

    /**
     * 释放预扣的额度
     */
    public void releaseLimit(Long userId, BigDecimal amount) {
        String key = LIMIT_KEY_PREFIX + userId;
        redisTemplate.execute(
                releaseScript,
                Collections.singletonList(key),
                amount.toPlainString()
        );
    }

    /**
     * 从数据库加载用户额度到Redis（懒加载）
     * 使用双重检查锁定防止并发加载
     */
    private void loadLimitFromDb(Long userId) {
        String key = LIMIT_KEY_PREFIX + userId;

        // 二次检查，防止并发
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }

        // 从数据库读取用户额度
        BigDecimal availableLimit = userLimitRepository.getAvailableLimit(userId);
        if (availableLimit == null) {
            // 用户不存在或额度为0，设置一个标记值
            availableLimit = BigDecimal.ZERO;
        }

        // 写入Redis并设置过期时间
        redisTemplate.opsForValue().set(key, availableLimit.toPlainString(), CACHE_EXPIRE);
    }
}
