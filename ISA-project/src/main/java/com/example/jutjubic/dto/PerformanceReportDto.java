package com.example.jutjubic.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregated performance report for analysis.
 * Contains statistics and individual measurements for performance comparison.
 */
@Getter
@Setter
@NoArgsConstructor
public class PerformanceReportDto {
    private String reportType;                          // "NEARBY_PERFORMANCE", "TRENDING_PERFORMANCE", "COMPARISON"
    private int totalMeasurements;
    private double avgResponseTimeMs;
    private double minResponseTimeMs;
    private double maxResponseTimeMs;
    private double medianResponseTimeMs;
    private double p95ResponseTimeMs;                   // 95th percentile
    private double p99ResponseTimeMs;                   // 99th percentile
    private int cacheHits;
    private int cacheMisses;
    private double cacheHitRatio;
    private List<PerformanceMetricDto> measurements;    // Individual measurements
    private String recommendation;                      // Optimization recommendation

    /**
     * Create a report from a list of metrics.
     */
    public static PerformanceReportDto fromMetrics(String reportType, List<PerformanceMetricDto> metrics) {
        PerformanceReportDto report = new PerformanceReportDto();
        report.setReportType(reportType);
        report.setMeasurements(new ArrayList<>(metrics));
        report.setTotalMeasurements(metrics.size());

        if (metrics.isEmpty()) {
            report.setAvgResponseTimeMs(0);
            report.setMinResponseTimeMs(0);
            report.setMaxResponseTimeMs(0);
            report.setMedianResponseTimeMs(0);
            report.setP95ResponseTimeMs(0);
            report.setP99ResponseTimeMs(0);
            report.setCacheHits(0);
            report.setCacheMisses(0);
            report.setCacheHitRatio(0);
            report.setRecommendation("No data available for analysis.");
            return report;
        }

        // Calculate statistics
        DoubleSummaryStatistics stats = metrics.stream()
                .mapToDouble(PerformanceMetricDto::getResponseTimeMs)
                .summaryStatistics();

        report.setAvgResponseTimeMs(Math.round(stats.getAverage() * 100.0) / 100.0);
        report.setMinResponseTimeMs(stats.getMin());
        report.setMaxResponseTimeMs(stats.getMax());

        // Sort for percentile calculations
        List<Long> sortedTimes = metrics.stream()
                .map(PerformanceMetricDto::getResponseTimeMs)
                .sorted()
                .collect(Collectors.toList());

        report.setMedianResponseTimeMs(calculatePercentile(sortedTimes, 50));
        report.setP95ResponseTimeMs(calculatePercentile(sortedTimes, 95));
        report.setP99ResponseTimeMs(calculatePercentile(sortedTimes, 99));

        // Cache statistics
        long hits = metrics.stream().filter(m -> "HIT".equals(m.getCacheStatus())).count();
        long misses = metrics.stream().filter(m -> "MISS".equals(m.getCacheStatus())).count();
        report.setCacheHits((int) hits);
        report.setCacheMisses((int) misses);
        report.setCacheHitRatio(hits + misses > 0 ? (double) hits / (hits + misses) : 0);

        // Generate recommendation
        report.setRecommendation(generateRecommendation(report));

        return report;
    }

    private static double calculatePercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    private static String generateRecommendation(PerformanceReportDto report) {
        StringBuilder rec = new StringBuilder();

        // Response time analysis
        if (report.getAvgResponseTimeMs() > 500) {
            rec.append("HIGH LATENCY DETECTED: Average response time exceeds 500ms. ");
            rec.append("Consider increasing cache TTL or optimizing database indexes. ");
        } else if (report.getAvgResponseTimeMs() > 200) {
            rec.append("MODERATE LATENCY: Response times are acceptable but could be improved. ");
        } else {
            rec.append("GOOD PERFORMANCE: Response times are within optimal range (<200ms). ");
        }

        // Cache analysis
        if (report.getCacheHitRatio() < 0.5 && report.getTotalMeasurements() > 10) {
            rec.append("LOW CACHE HIT RATIO: Consider adjusting cache strategy or increasing TTL. ");
        } else if (report.getCacheHitRatio() > 0.8) {
            rec.append("EXCELLENT CACHE UTILIZATION. ");
        }

        // P95/P99 analysis
        if (report.getP99ResponseTimeMs() > report.getAvgResponseTimeMs() * 3) {
            rec.append("HIGH VARIANCE: P99 latency is significantly higher than average. Check for outliers or resource contention.");
        }

        return rec.toString().trim();
    }
}
