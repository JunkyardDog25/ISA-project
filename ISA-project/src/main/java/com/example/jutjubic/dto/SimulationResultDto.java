package com.example.jutjubic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * DTO for simulation test results.
 * Used for [S3] requirement - testing with requests from multiple regions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResultDto {

    private String simulationType;              // "CONCENTRATED", "DISTRIBUTED", "MIXED"
    private int totalRequests;
    private int successfulRequests;
    private int failedRequests;
    private double successRate;

    // Response time statistics
    private double avgResponseTimeMs;
    private double minResponseTimeMs;
    private double maxResponseTimeMs;
    private double medianResponseTimeMs;
    private double p95ResponseTimeMs;
    private double p99ResponseTimeMs;

    // Throughput
    private double requestsPerSecond;
    private long totalDurationMs;

    // Regional breakdown
    private Map<String, RegionStats> regionStats;

    // Comparison with baseline
    private Double baselineAvgMs;
    private Double performanceDegradationPercent;

    // Analysis
    private String analysis;
    private List<String> recommendations;

    /**
     * Statistics for a specific region/area.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionStats {
        private String regionName;
        private double latitude;
        private double longitude;
        private int requestCount;
        private double avgResponseTimeMs;
        private int resultCount;
        private double radiusKm;
    }
}
