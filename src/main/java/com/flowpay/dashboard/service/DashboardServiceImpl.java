package com.flowpay.dashboard.service;

import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.dashboard.dto.DashboardChartsResponse;
import com.flowpay.dashboard.dto.DashboardStatsResponse;
import com.flowpay.dashboard.dto.RealtimeMetricsResponse;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.repository.AccountRepository;
import com.flowpay.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(UUID userId) {
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        OffsetDateTime now = OffsetDateTime.now();

        long totalTransactions = transactionRepository.countByUserIdAndDateRange(userId, thirtyDaysAgo, now);
        long pendingTransactions = transactionRepository.countByStatus(TransactionStatus.PENDING);
        long completedTransactions = transactionRepository.countByStatus(TransactionStatus.COMPLETED);
        long failedTransactions = transactionRepository.countByStatus(TransactionStatus.FAILED);

        long totalAccounts = accountRepository.countByUserId(userId);
        long activeAccounts = accountRepository.findByUserIdAndStatus(userId, AccountStatus.ACTIVE).size();
        BigDecimal totalBalance = accountRepository.getTotalBalanceByUserId(userId);

        BigDecimal totalVolume = transactionRepository.getTotalAmountBySenderIdAndDateRange(userId, thirtyDaysAgo, now);
        BigDecimal totalReceived = transactionRepository.getTotalAmountByReceiverIdAndDateRange(userId, thirtyDaysAgo, now);

        return DashboardStatsResponse.builder()
                .totalTransactions(totalTransactions)
                .pendingTransactions(pendingTransactions)
                .completedTransactions(completedTransactions)
                .failedTransactions(failedTransactions)
                .totalAccounts(totalAccounts)
                .activeAccounts(activeAccounts)
                .totalBalance(totalBalance)
                .totalVolume(totalVolume.add(totalReceived))
                .totalRevenue(BigDecimal.ZERO)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardChartsResponse getCharts(UUID userId, int days) {
        List<DashboardChartsResponse.VolumeDataPoint> volume = getTransactionVolume(userId, days);
        Map<String, Long> statusDist = getStatusDistribution(userId);
        List<DashboardChartsResponse.RevenueDataPoint> revenue = getRevenue(userId, days);

        return DashboardChartsResponse.builder()
                .transactionVolume(volume)
                .statusDistribution(statusDist)
                .revenue(revenue)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DashboardChartsResponse.VolumeDataPoint> getTransactionVolume(UUID userId, int days) {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(days);
        OffsetDateTime endDate = OffsetDateTime.now();

        List<Transaction> transactions = transactionRepository.findByDateRange(startDate, endDate);

        Map<LocalDate, List<Transaction>> grouped = transactions.stream()
                .filter(t -> t.getSender().getId().equals(userId) || t.getReceiver().getId().equals(userId))
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().toLocalDate()));

        List<DashboardChartsResponse.VolumeDataPoint> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            List<Transaction> dayTransactions = grouped.getOrDefault(date, Collections.emptyList());
            BigDecimal amount = dayTransactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(DashboardChartsResponse.VolumeDataPoint.builder()
                    .date(date)
                    .count(dayTransactions.size())
                    .amount(amount)
                    .build());
        }
        Collections.reverse(result);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getStatusDistribution(UUID userId) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (TransactionStatus status : TransactionStatus.values()) {
            long count = transactionRepository.countByStatus(status);
            distribution.put(status.name().toLowerCase(), count);
        }
        return distribution;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DashboardChartsResponse.RevenueDataPoint> getRevenue(UUID userId, int days) {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(days);
        OffsetDateTime endDate = OffsetDateTime.now();

        List<Transaction> transactions = transactionRepository.findByDateRange(startDate, endDate);

        Map<LocalDate, BigDecimal> feesByDay = transactions.stream()
                .filter(t -> t.getFee() != null && t.getFee().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().toLocalDate(),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getFee, BigDecimal::add)
                ));

        List<DashboardChartsResponse.RevenueDataPoint> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            BigDecimal fees = feesByDay.getOrDefault(date, BigDecimal.ZERO);
            result.add(DashboardChartsResponse.RevenueDataPoint.builder()
                    .date(date)
                    .fees(fees)
                    .build());
        }
        Collections.reverse(result);
        return result;
    }

    @Override
    public RealtimeMetricsResponse getRealtimeMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long uptime = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        double cpuUsage = 0.0;
        try {
            io.micrometer.core.instrument.Gauge cpuGauge = meterRegistry.find("system.cpu.usage").gauge();
            if (cpuGauge != null) {
                cpuUsage = cpuGauge.value() * 100;
            }
        } catch (Exception e) {
            log.debug("Could not read CPU metric: {}", e.getMessage());
        }

        long pendingCount = transactionRepository.countByStatus(TransactionStatus.PENDING);

        return RealtimeMetricsResponse.builder()
                .activeUsers(0)
                .transactionsPerMinute(0)
                .averageResponseTime(0.0)
                .pendingQueue(pendingCount)
                .systemCpuUsage(cpuUsage)
                .memoryUsedMb(memoryUsed)
                .uptimeSeconds(uptime)
                .build();
    }
}
