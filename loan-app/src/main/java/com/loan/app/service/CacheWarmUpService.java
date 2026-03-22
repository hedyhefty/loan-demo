package com.loan.app.service;

import com.loan.domain.user.repository.UserLimitRepository;
import com.loan.infra.common.redis.RedisLimitManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缓存预热服务
 *
 * <ul>
 *   <li>启动预热：系统启动后自动加载所有活跃用户额度到 Redis</li>
 *   <li>定时刷新：每小时主动刷新即将过期的缓存，防止缓存雪崩</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmUpService implements ApplicationRunner {

    private final UserLimitRepository userLimitRepository;
    private final RedisLimitManager redisLimitManager;

    /**
     * 启动时执行全量预热
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("【缓存预热】系统启动，开始预热...");
        try {
            List<Long> activeUserIds = userLimitRepository.findAllActiveUserIds();
            if (activeUserIds.isEmpty()) {
                log.info("【缓存预热】未找到活跃用户，预热跳过");
                return;
            }
            redisLimitManager.warmUp(activeUserIds);
            log.info("【缓存预热】系统启动预热完成，共加载 {} 个用户", activeUserIds.size());
        } catch (Exception e) {
            log.error("【缓存预热】启动预热失败", e);
        }
    }

    /**
     * 定时刷新即将过期的缓存（每小时执行一次）
     * <p>防止缓存集中过期导致缓存雪崩（大量请求击穿到 DB）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledRefresh() {
        log.debug("【缓存刷新】开始定时刷新即将过期的缓存...");
        try {
            List<Long> activeUserIds = userLimitRepository.findAllActiveUserIds();
            redisLimitManager.refreshExpiringKeys(activeUserIds);
        } catch (Exception e) {
            log.error("【缓存刷新】定时刷新失败", e);
        }
    }
}
