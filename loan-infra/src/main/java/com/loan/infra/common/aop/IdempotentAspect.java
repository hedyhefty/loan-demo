package com.loan.infra.common.aop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext; // 修正了这里的路径
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;

    // 预创建解析器，线程安全
    private final ExpressionParser parser = new SpelExpressionParser();
    // Spring 提供的参数名发现器，比原生的 Method.getParameterNames() 靠谱得多
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 1. 生成唯一锁 Key
        String lockKey = generateKey(joinPoint, idempotent);

        // 2. 尝试抢占 Redis 锁 (SETNX)
        // 只有第一次请求能成功设置，并带有过期时间
        Boolean success = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                "processing",
                Duration.ofSeconds(idempotent.expire())
        );

        if (Boolean.FALSE.equals(success)) {
            log.warn("【幂等拦截】检测到重复请求，已拦截 Key: {}", lockKey);
            // 这里返回 null，对于 void 方法或不需要返回值的 MQ 消费者来说是安全的
            return null;
        }

        try {
            // 3. 执行真正的业务逻辑
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 4. 业务执行异常，删除锁，允许下次重试（比如网络抖动导致的数据库失败）
            redisTemplate.delete(lockKey);
            log.error("【幂等组件】业务执行异常，已释放锁 Key: {}", lockKey);
            throw e;
        }
        // 执行成功不删锁，利用过期时间建立“屏蔽窗口”
    }

    private String generateKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        // 使用 Spring 的工具类获取参数名（如 "event"）
        String[] paramNames = nameDiscoverer.getParameterNames(method);

        // 构造解析上下文
        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        try {
            // 解析 SpEL 表达式，如将 "#event.orderNo" 变为真正的单号 "REQ_123"
            Object value = parser.parseExpression(idempotent.key()).getValue(context);
            return idempotent.prefix() + (value == null ? "" : value.toString());
        } catch (Exception e) {
            log.error("【幂等组件】SpEL 表达式解析失败: {}", idempotent.key(), e);
            throw new RuntimeException("幂等组件配置错误");
        }
    }
}