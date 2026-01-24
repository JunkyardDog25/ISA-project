package com.example.jutjubic.services;

import com.example.jutjubic.models.DailyPopularVideo;
import com.example.jutjubic.repositories.DailyPopularVideoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for retrieving daily popular (trending) videos.
 * Includes performance metrics tracking for [S2] requirement.
 */
@Service
public class DailyPopularVideosService {
    private final DailyPopularVideoRepository dailyPopularVideoRepository;
    private final PerformanceMetricsService performanceMetricsService;

    public DailyPopularVideosService(DailyPopularVideoRepository dailyPopularVideoRepository,
                                     PerformanceMetricsService performanceMetricsService) {
        this.dailyPopularVideoRepository = dailyPopularVideoRepository;
        this.performanceMetricsService = performanceMetricsService;
    }

    /**
     * Get all daily popular videos with performance tracking.
     * @return List of trending videos
     */
    public List<DailyPopularVideo> getAll() {
        long startTime = System.currentTimeMillis();

        List<DailyPopularVideo> result = dailyPopularVideoRepository.findAll();

        // Record performance metric for trending fetch
        long responseTime = System.currentTimeMillis() - startTime;
        performanceMetricsService.recordMetric("TRENDING_FETCH", responseTime, result.size());

        return result;
    }
}
