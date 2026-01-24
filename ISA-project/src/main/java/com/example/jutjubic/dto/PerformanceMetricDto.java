package com.example.jutjubic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * DTO for performance metrics of nearby/trending video queries.
 * Used for measuring and comparing real-time vs cached performance.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetricDto {
    private String operationType;      // "NEARBY_SEARCH", "TRENDING_FETCH", "TRENDING_COMPUTE"
    private long responseTimeMs;       // Response time in milliseconds
    private int resultCount;           // Number of results returned
    private double radiusKm;           // Search radius (for nearby)
    private String cacheStatus;        // "HIT", "MISS", "DISABLED"
    private Instant timestamp;         // When the measurement was taken
    private String location;           // "lat,lon" or "N/A"

    public PerformanceMetricDto(String operationType, long responseTimeMs, int resultCount) {
        this.operationType = operationType;
        this.responseTimeMs = responseTimeMs;
        this.resultCount = resultCount;
        this.timestamp = Instant.now();
        this.cacheStatus = "N/A";
        this.location = "N/A";
        this.radiusKm = 0;
    }
}
