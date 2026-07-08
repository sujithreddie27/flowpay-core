package com.flowpay.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${flowpay.async.core-pool-size:8}")
    private int corePoolSize;

    @Value("${flowpay.async.max-pool-size:32}")
    private int maxPoolSize;

    @Value("${flowpay.async.queue-capacity:500}")
    private int queueCapacity;

    @Value("${flowpay.async.batch-core-pool-size:4}")
    private int batchCorePoolSize;

    @Value("${flowpay.async.batch-max-pool-size:16}")
    private int batchMaxPoolSize;

    @Value("${flowpay.async.batch-queue-capacity:200}")
    private int batchQueueCapacity;

    @Bean(name = "taskExecutor")
    public Executor taskExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("flowpay-async-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(rejectedHandler("taskExecutor"));
        executor.initialize();

        monitorExecutor(executor, "async.task.executor", meterRegistry);
        return executor;
    }

    @Bean(name = "batchProcessingExecutor")
    public Executor batchProcessingExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchCorePoolSize);
        executor.setMaxPoolSize(batchMaxPoolSize);
        executor.setQueueCapacity(batchQueueCapacity);
        executor.setThreadNamePrefix("flowpay-batch-");
        executor.setKeepAliveSeconds(120);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(rejectedHandler("batchProcessingExecutor"));
        executor.initialize();

        monitorExecutor(executor, "async.batch.executor", meterRegistry);
        return executor;
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("flowpay-notify-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.setRejectedExecutionHandler(rejectedHandler("notificationExecutor"));
        executor.initialize();

        monitorExecutor(executor, "async.notification.executor", meterRegistry);
        return executor;
    }

    private RejectedExecutionHandler rejectedHandler(String executorName) {
        return (runnable, executor) -> {
            log.warn("Task rejected from {}: pool={}, active={}, queue={}",
                    executorName, executor.getPoolSize(),
                    executor.getActiveCount(), executor.getQueue().size());
            // Use caller-runs policy for backpressure
            if (!executor.isShutdown()) {
                runnable.run();
            }
        };
    }

    private void monitorExecutor(ThreadPoolTaskExecutor executor, String name, MeterRegistry meterRegistry) {
        meterRegistry.gauge(name + ".pool.size", executor, e -> e.getThreadPoolExecutor().getPoolSize());
        meterRegistry.gauge(name + ".active.count", executor, e -> e.getThreadPoolExecutor().getActiveCount());
        meterRegistry.gauge(name + ".queue.size", executor, e -> e.getThreadPoolExecutor().getQueue().size());
        meterRegistry.gauge(name + ".queue.remaining", executor,
                e -> e.getThreadPoolExecutor().getQueue().remainingCapacity());
    }
}
