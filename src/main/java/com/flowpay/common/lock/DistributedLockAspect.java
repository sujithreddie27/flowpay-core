package com.flowpay.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flowpay.distributed-lock.enabled", havingValue = "true", matchIfMissing = true)
public class DistributedLockAspect {

    private final DistributedLockService distributedLockService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Value("${flowpay.distributed-lock.wait-time:5000}")
    private long defaultWaitTimeMs;

    @Value("${flowpay.distributed-lock.lease-time:30000}")
    private long defaultLeaseTimeMs;

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = resolveKey(distributedLock.key(), joinPoint);
        long waitTime = distributedLock.waitTime() > 0 ? distributedLock.waitTime() : defaultWaitTimeMs;
        long leaseTime = distributedLock.leaseTime() > 0 ? distributedLock.leaseTime() : defaultLeaseTimeMs;

        return distributedLockService.executeWithLock(lockKey, waitTime, leaseTime, () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(e);
            }
        });
    }

    private String resolveKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        if (!keyExpression.contains("#")) {
            return keyExpression;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        Object value = parser.parseExpression(keyExpression).getValue(context);
        return value != null ? value.toString() : keyExpression;
    }
}
