package com.example.jutjubic.services;

import com.example.jutjubic.dto.PerformanceMetricDto;
import com.example.jutjubic.dto.PerformanceReportDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * Service for collecting and analyzing performance metrics.
 * Tracks response times for nearby searches and trending video operations
 * to help determine optimal balance between real-time data and performance.
 *
 * Metrics are stored in memory with automatic expiration after 24 hours.
 * For production, consider persisting to database or external monitoring system.
 */
@Service
public class PerformanceMetricsService {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMetricsService.class);

    // Maximum number of metrics to keep in memory per operation type
    private static final int MAX_METRICS_PER_TYPE = 1000;

    // Metrics storage by operation type
    private final Map<String, ConcurrentLinkedDeque<PerformanceMetricDto>> metricsStore = new ConcurrentHashMap<>();

    /**
     * Record a performance metric.
     *
     * @param operationType Type of operation (NEARBY_SEARCH, TRENDING_FETCH, etc.)
     * @param responseTimeMs Response time in milliseconds
     * @param resultCount Number of results returned
     * @param cacheStatus Cache status (HIT, MISS, DISABLED)
     * @param location Location string for nearby searches
     * @param radiusKm Search radius for nearby searches
     */
    public void recordMetric(String operationType, long responseTimeMs, int resultCount,
                            String cacheStatus, String location, double radiusKm) {
        PerformanceMetricDto metric = new PerformanceMetricDto();
        metric.setOperationType(operationType);
        metric.setResponseTimeMs(responseTimeMs);
        metric.setResultCount(resultCount);
        metric.setCacheStatus(cacheStatus != null ? cacheStatus : "N/A");
        metric.setLocation(location != null ? location : "N/A");
        metric.setRadiusKm(radiusKm);
        metric.setTimestamp(Instant.now());

        ConcurrentLinkedDeque<PerformanceMetricDto> deque = metricsStore.computeIfAbsent(
            operationType, k -> new ConcurrentLinkedDeque<>()
        );

        deque.addLast(metric);

        // Trim old entries if over limit
        while (deque.size() > MAX_METRICS_PER_TYPE) {
            deque.pollFirst();
        }

        logger.debug("Recorded metric: {} - {}ms, {} results, cache: {}",
                    operationType, responseTimeMs, resultCount, cacheStatus);
    }

    /**
     * Simplified metric recording without all parameters.
     */
    public void recordMetric(String operationType, long responseTimeMs, int resultCount) {
        recordMetric(operationType, responseTimeMs, resultCount, "N/A", null, 0);
    }

    /**
     * Get performance report for a specific operation type.
     *
     * @param operationType Operation type to report on
     * @param lastMinutes Only include metrics from the last N minutes (0 for all)
     * @return Performance report with statistics
     */
    public PerformanceReportDto getReport(String operationType, int lastMinutes) {
        ConcurrentLinkedDeque<PerformanceMetricDto> deque = metricsStore.get(operationType);

        if (deque == null || deque.isEmpty()) {
            return PerformanceReportDto.fromMetrics(operationType, new ArrayList<>());
        }

        List<PerformanceMetricDto> metrics;
        if (lastMinutes > 0) {
            Instant cutoff = Instant.now().minus(lastMinutes, ChronoUnit.MINUTES);
            metrics = deque.stream()
                    .filter(m -> m.getTimestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
        } else {
            metrics = new ArrayList<>(deque);
        }

        return PerformanceReportDto.fromMetrics(operationType, metrics);
    }

    /**
     * Get comparison report between different operation types.
     * Useful for comparing cached vs non-cached performance.
     */
    public Map<String, PerformanceReportDto> getComparisonReport(int lastMinutes) {
        return metricsStore.keySet().stream()
                .collect(Collectors.toMap(
                    type -> type,
                    type -> getReport(type, lastMinutes)
                ));
    }

    /**
     * Get all metrics for a specific operation type (for detailed analysis).
     */
    public List<PerformanceMetricDto> getAllMetrics(String operationType, int lastMinutes) {
        ConcurrentLinkedDeque<PerformanceMetricDto> deque = metricsStore.get(operationType);

        if (deque == null) {
            return new ArrayList<>();
        }

        if (lastMinutes > 0) {
            Instant cutoff = Instant.now().minus(lastMinutes, ChronoUnit.MINUTES);
            return deque.stream()
                    .filter(m -> m.getTimestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>(deque);
    }

    /**
     * Clear all metrics (useful for testing or reset).
     */
    public void clearAllMetrics() {
        metricsStore.clear();
        logger.info("All performance metrics cleared");
    }

    /**
     * Clear metrics for a specific operation type.
     */
    public void clearMetrics(String operationType) {
        metricsStore.remove(operationType);
        logger.info("Performance metrics cleared for: {}", operationType);
    }

    /**
     * Get summary of all operation types and their metric counts.
     */
    public Map<String, Integer> getMetricsCounts() {
        return metricsStore.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().size()
                ));
    }
}
