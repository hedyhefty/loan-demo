package com.loan.infra.common.redis;

import com.loan.domain.user.repository.UserLimitRepository;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.TimeUnit;

/**
 * 缓存三兄弟升级方案：
 * <ul>
 *   <li>防穿透：空值标记 + 短过期时间</li>
 *   <li>防击穿：分布式锁 + 单一加载（single-flight）</li>
 *   <li>预热：启动时批量加载热点数据</li>
 * </ul>
 */
@Slf4j
@Component
public class RedisLimitManager {

    private final StringRedisTemplate redisTemplate;
    private final UserLimitRepository userLimitRepository;
    private final RedisLockManager redisLockManager;
    private DefaultRedisScript<Long> limitScript;
    private DefaultRedisScript<Long> releaseScript;

    private static final String LIMIT_KEY_PREFIX = "loan:limit:user:";
    private static final String LOCK_KEY_PREFIX = "lock:limit:user:";
    private static final String NULL_MARKER = "NULL";          // 空值标记（防穿透）
    private static final Duration CACHE_EXPIRE = Duration.ofHours(2);   // 缓存过期时间
    private static final Duration NULL_TTL = Duration.ofMinutes(5);      // 空值短TTL（防穿透）
    private static final Duration LOCK_EXPIRE = Duration.ofSeconds(10);  // 分布式锁过期

    public RedisLimitManager(StringRedisTemplate redisTemplate,
                            @Lazy UserLimitRepository userLimitRepository,
                            @Lazy RedisLockManager redisLockManager) {
        this.redisTemplate = redisTemplate;
        this.userLimitRepository = userLimitRepository;
        this.redisLockManager = redisLockManager;
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
     * 尝试预扣用户额度（原子操作）
     *
     * <p>防穿透：如果用户不存在，缓存空值标记 5 分钟，避免大量请求穿透到 DB。
     * <p>防击穿：使用分布式锁确保只有一个请求从 DB 加载，其余请求等待。
     *
     * @param userId 用户ID
     * @param amount 申请金额
     * @return true=预扣成功，false=额度不足或用户不存在
     */
    public boolean tryAcquireLimit(Long userId, BigDecimal amount) {
        String key = LIMIT_KEY_PREFIX + userId;

        // 1. 读取缓存（包含穿透保护）
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            // 命中空值标记 → 用户不存在，直接返回额度不足
            if (NULL_MARKER.equals(cached)) {
                return false;
            }
            // 执行 Lua 原子扣减
            return executeLimit扣减(key, amount);
        }

        // 2. 缓存 miss → 尝试从 DB 加载（带分布式锁，防止击穿）
        return loadWithLock(userId, amount);
    }

    /**
     * 带分布式锁的 DB 加载（single-flight 模式）
     * <p>只有一个请求从 DB 加载数据并写入缓存，其他请求等待后直接从缓存读取。
     */
    private boolean loadWithLock(Long userId, BigDecimal amount) {
        String key = LIMIT_KEY_PREFIX + userId;
        String lockKey = LOCK_KEY_PREFIX + userId;

        // 尝试获取分布式锁（single-flight）
        boolean locked = redisLockManager.tryLock(lockKey, LOCK_EXPIRE);
        if (!locked) {
            // 未获取到锁，说明另一个请求正在加载，等待后重试读取
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null && !NULL_MARKER.equals(cached)) {
                return executeLimit扣减(key, amount);
            }
            // 等待后仍 miss，以额度不足处理
            return false;
        }

        try {
            // 获取到锁，从 DB 加载
            BigDecimal availableLimit = userLimitRepository.getAvailableLimit(userId);

            if (availableLimit == null) {
                // 用户不存在 → 写入空值标记（防穿透）
                redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_TTL);
                log.debug("【缓存穿透】用户 {} 不存在，缓存空值 {} 秒", userId, NULL_TTL.toSeconds());
                return false;
            }

            // 写入缓存
            redisTemplate.opsForValue().set(key, availableLimit.toPlainString(), CACHE_EXPIRE);
            return executeLimit扣减(key, amount);

        } finally {
            redisLockManager.unlock(lockKey);
        }
    }

    /**
     * 执行 Lua 脚本原子扣减
     */
    private boolean executeLimit扣减(String key, BigDecimal amount) {
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
     * 强制从 MySQL 同步额度到 Redis（用于 MySQL 实扣后保持两端一致）
     */
    public void syncFromDb(Long userId) {
        String key = LIMIT_KEY_PREFIX + userId;
        BigDecimal availableLimit = userLimitRepository.getAvailableLimit(userId);
        if (availableLimit == null) {
            availableLimit = BigDecimal.ZERO;
        }
        // 随机 TTL 偏移量 ±30%，防止多用户同时同步导致批量过期
        long jitterSeconds = (long) (Math.random() * CACHE_EXPIRE.toSeconds() * 0.3);
        long actualTtlSeconds = CACHE_EXPIRE.toSeconds() - jitterSeconds;
        redisTemplate.opsForValue().set(key, availableLimit.toPlainString(), Duration.ofSeconds(actualTtlSeconds));
    }

    /**
     * ========== 预热相关 ==========
     */

    /**
     * 缓存预热：将热点用户额度提前加载到 Redis
     * <p>调用时机：
     * <ul>
     *   <li>系统重启后（通过 @PostConstruct 或 ApplicationRunner）</li>
     *   <li>促销活动前手动触发</li>
     *   <li>定时任务，每天凌晨自动执行</li>
     * </ul>
     *
     * @param userIds 需要预热的用户 ID 列表
     */
    public void warmUp(java.util.List<Long> userIds) {
        log.info("【缓存预热】开始预热 {} 个用户额度", userIds.size());
        int success = 0;
        int failed = 0;

        for (Long userId : userIds) {
            try {
                String key = LIMIT_KEY_PREFIX + userId;
                BigDecimal availableLimit = userLimitRepository.getAvailableLimit(userId);

                if (availableLimit != null) {
                    // 随机 TTL 偏移量 ±30%，防止预热时所有 key 同时过期导致雪崩
                    long jitterSeconds = (long) (Math.random() * CACHE_EXPIRE.toSeconds() * 0.3);
                    long actualTtlSeconds = CACHE_EXPIRE.toSeconds() - jitterSeconds;
                    redisTemplate.opsForValue().set(key, availableLimit.toPlainString(), Duration.ofSeconds(actualTtlSeconds));
                    success++;
                } else {
                    // 不存在用户的也写入空值，防止穿透
                    redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_TTL);
                }
            } catch (Exception e) {
                log.warn("【缓存预热】用户 {} 预热失败: {}", userId, e.getMessage());
                failed++;
            }
        }

        log.info("【缓存预热】完成: 成功={}, 失败={}", success, failed);
    }

    /**
     * 主动刷新即将过期的热点缓存（防缓存雪崩）
     * <p>扫描 TTL 低于阈值的所有缓存 key，提前从 DB 刷新。
     * 建议配合定时任务（如每 10 分钟执行一次）。
     */
    public void refreshExpiringKeys(java.util.List<Long> userIds) {
        log.debug("【缓存刷新】开始刷新即将过期的缓存...");
        for (Long userId : userIds) {
            String key = LIMIT_KEY_PREFIX + userId;
            Long ttl = redisTemplate.getExpire(key);
            // TTL < 10 分钟且不是空值，触发主动刷新
            if (ttl != null && ttl > 0 && ttl < 600 && !NULL_MARKER.equals(redisTemplate.opsForValue().get(key))) {
                syncFromDb(userId);
                log.debug("【缓存刷新】已刷新用户 {} 的缓存", userId);
            }
        }
    }
}
