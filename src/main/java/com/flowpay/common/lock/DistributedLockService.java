package com.flowpay.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flowpay.distributed-lock.enabled", havingValue = "true", matchIfMissing = true)
public class DistributedLockService {

    private static final String LOCK_PREFIX = "flowpay:lock:";

    private final RedissonClient redissonClient;

    @Value("${flowpay.distributed-lock.wait-time:5000}")
    private long defaultWaitTimeMs;

    @Value("${flowpay.distributed-lock.lease-time:30000}")
    private long defaultLeaseTimeMs;

    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        return executeWithLock(lockKey, defaultWaitTimeMs, defaultLeaseTimeMs, action);
    }

    public <T> T executeWithLock(String lockKey, long waitTimeMs, long leaseTimeMs, Supplier<T> action) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockKey);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(waitTimeMs, leaseTimeMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new DistributedLockException("Failed to acquire lock: " + lockKey);
            }
            log.debug("Lock acquired: key={}", lockKey);
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockException("Lock acquisition interrupted: " + lockKey, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: key={}", lockKey);
            }
        }
    }

    public void executeWithLock(String lockKey, Runnable action) {
        executeWithLock(lockKey, () -> {
            action.run();
            return null;
        });
    }

    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, defaultWaitTimeMs, defaultLeaseTimeMs);
    }

    public boolean tryLock(String lockKey, long waitTimeMs, long leaseTimeMs) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockKey);
        try {
            return lock.tryLock(waitTimeMs, leaseTimeMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released: key={}", lockKey);
        }
    }
}
