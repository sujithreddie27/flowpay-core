package com.flowpay.cache;

import com.flowpay.common.lock.DistributedLockException;
import com.flowpay.common.lock.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Distributed Lock Service Tests")
class DistributedLockServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    private DistributedLockService distributedLockService;

    @BeforeEach
    void setUp() {
        distributedLockService = new DistributedLockService(redissonClient);
        ReflectionTestUtils.setField(distributedLockService, "defaultWaitTimeMs", 5000L);
        ReflectionTestUtils.setField(distributedLockService, "defaultLeaseTimeMs", 30000L);
    }

    @Nested
    @DisplayName("executeWithLock")
    class ExecuteWithLock {

        @Test
        @DisplayName("Should execute action when lock is acquired")
        void shouldExecuteWhenLockAcquired() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            String result = distributedLockService.executeWithLock("test-key", () -> "success");

            assertThat(result).isEqualTo("success");
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("Should throw exception when lock cannot be acquired")
        void shouldThrowWhenLockNotAcquired() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

            assertThatThrownBy(() -> distributedLockService.executeWithLock("test-key", () -> "value"))
                    .isInstanceOf(DistributedLockException.class)
                    .hasMessageContaining("Failed to acquire lock");
        }

        @Test
        @DisplayName("Should release lock even if action throws exception")
        void shouldReleaseLockOnException() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            assertThatThrownBy(() ->
                    distributedLockService.executeWithLock("test-key", () -> {
                        throw new RuntimeException("action failed");
                    })
            ).isInstanceOf(RuntimeException.class);

            verify(rLock).unlock();
        }

        @Test
        @DisplayName("Should handle interrupted exception")
        void shouldHandleInterruptedException() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenThrow(new InterruptedException("interrupted"));

            assertThatThrownBy(() -> distributedLockService.executeWithLock("test-key", () -> "value"))
                    .isInstanceOf(DistributedLockException.class)
                    .hasMessageContaining("interrupted");

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            // Clear interrupt flag
            Thread.interrupted();
        }

        @Test
        @DisplayName("Should execute Runnable with lock")
        void shouldExecuteRunnableWithLock() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            AtomicBoolean executed = new AtomicBoolean(false);
            distributedLockService.executeWithLock("test-key", () -> executed.set(true));

            assertThat(executed.get()).isTrue();
            verify(rLock).unlock();
        }
    }

    @Nested
    @DisplayName("tryLock and unlock")
    class TryLockAndUnlock {

        @Test
        @DisplayName("Should return true when lock acquired")
        void shouldReturnTrueWhenAcquired() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            boolean result = distributedLockService.tryLock("test-key");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when lock not acquired")
        void shouldReturnFalseWhenNotAcquired() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

            boolean result = distributedLockService.tryLock("test-key");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should unlock when held by current thread")
        void shouldUnlockWhenHeldByThread() {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            distributedLockService.unlock("test-key");

            verify(rLock).unlock();
        }

        @Test
        @DisplayName("Should not unlock when not held by current thread")
        void shouldNotUnlockWhenNotHeldByThread() {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.isHeldByCurrentThread()).thenReturn(false);

            distributedLockService.unlock("test-key");

            verify(rLock, never()).unlock();
        }
    }
}
