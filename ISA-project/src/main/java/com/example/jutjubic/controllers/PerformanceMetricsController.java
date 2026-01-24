package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.PerformanceMetricDto;
import com.example.jutjubic.dto.PerformanceReportDto;
import com.example.jutjubic.services.PerformanceMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for performance metrics and analysis.
 * Provides endpoints for viewing performance data to determine
 * optimal balance between real-time trending and performance.
 *
 * Used for requirement [S2]: Proving optimal measure between
 * performance and real-time trending with tabular/graphical display.
 */
@RestController
@RequestMapping("/api/performance")
public class PerformanceMetricsController {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMetricsController.class);

    private final PerformanceMetricsService metricsService;

    public PerformanceMetricsController(PerformanceMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * Get performance report for a specific operation type.
     *
     * @param operationType Operation type (NEARBY_SEARCH, TRENDING_FETCH, TRENDING_COMPUTE)
     * @param lastMinutes Only include metrics from last N minutes (default: 60, 0 for all)
     * @return Performance report with statistics and recommendations
     */
    @GetMapping("/report/{operationType}")
    public ResponseEntity<PerformanceReportDto> getReport(
            @PathVariable String operationType,
            @RequestParam(defaultValue = "60") int lastMinutes) {
        logger.debug("Getting performance report for {} (last {} minutes)", operationType, lastMinutes);
        PerformanceReportDto report = metricsService.getReport(operationType, lastMinutes);
        return ResponseEntity.ok(report);
    }

    /**
     * Get comparison report for all operation types.
     * Useful for comparing performance across different features.
     *
     * @param lastMinutes Time window in minutes (default: 60)
     * @return Map of operation type to performance report
     */
    @GetMapping("/comparison")
    public ResponseEntity<Map<String, PerformanceReportDto>> getComparisonReport(
            @RequestParam(defaultValue = "60") int lastMinutes) {
        logger.debug("Getting comparison report (last {} minutes)", lastMinutes);
        Map<String, PerformanceReportDto> reports = metricsService.getComparisonReport(lastMinutes);
        return ResponseEntity.ok(reports);
    }

    /**
     * Get raw metrics for detailed analysis and graphing.
     *
     * @param operationType Operation type
     * @param lastMinutes Time window (default: 60)
     * @return List of individual metrics
     */
    @GetMapping("/metrics/{operationType}")
    public ResponseEntity<List<PerformanceMetricDto>> getMetrics(
            @PathVariable String operationType,
            @RequestParam(defaultValue = "60") int lastMinutes) {
        logger.debug("Getting raw metrics for {} (last {} minutes)", operationType, lastMinutes);
        List<PerformanceMetricDto> metrics = metricsService.getAllMetrics(operationType, lastMinutes);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get summary of all tracked operation types.
     *
     * @return Map of operation type to metric count
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Integer>> getSummary() {
        return ResponseEntity.ok(metricsService.getMetricsCounts());
    }

    /**
     * Clear all metrics (admin/testing function).
     */
    @DeleteMapping("/clear")
    public ResponseEntity<String> clearAllMetrics() {
        metricsService.clearAllMetrics();
        return ResponseEntity.ok("All metrics cleared");
    }

    /**
     * Clear metrics for specific operation type.
     */
    @DeleteMapping("/clear/{operationType}")
    public ResponseEntity<String> clearMetrics(@PathVariable String operationType) {
        metricsService.clearMetrics(operationType);
        return ResponseEntity.ok("Metrics cleared for: " + operationType);
    }
}
