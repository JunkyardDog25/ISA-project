package com.example.jutjubic.utils;

import com.example.jutjubic.repositories.DailyPopularVideoRepository;
import com.example.jutjubic.services.PerformanceMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for daily trending video computation.
 * Includes performance metrics tracking for [S2] requirement.
 */
@Component
public class DailyJobScheduler {
    private static final Logger logger = LoggerFactory.getLogger(DailyJobScheduler.class);

    private final JobOperator jobOperator;
    private final Job job;
    private final DailyPopularVideoRepository dailyPopularVideoRepository;
    private final PerformanceMetricsService performanceMetricsService;

    public DailyJobScheduler(JobOperator jobOperator, Job job,
                            DailyPopularVideoRepository dailyPopularVideoRepository,
                            PerformanceMetricsService performanceMetricsService) {
        this.jobOperator = jobOperator;
        this.job = job;
        this.dailyPopularVideoRepository = dailyPopularVideoRepository;
        this.performanceMetricsService = performanceMetricsService;
    }

    /**
     * Run daily trending computation job.
     * Scheduled to run at 12:00 PM every day.
     * Performance is measured and recorded for analysis.
     */
    @Scheduled(cron = "0 0 12 * * *")
    public void runDailyJob() {
        logger.info("Starting scheduled daily batch job...");
        long startTime = System.currentTimeMillis();
        int resultCount = 0;

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("jobName", job.getName())
                    .toJobParameters();
            jobOperator.start(job, params);

            dailyPopularVideoRepository.keepOnlyTop3();
            resultCount = (int) dailyPopularVideoRepository.count();

        } catch (Exception e) {
            logger.error("Error executing scheduled job", e);
        }

        // Record performance metric for trending computation
        long responseTime = System.currentTimeMillis() - startTime;
        performanceMetricsService.recordMetric("TRENDING_COMPUTE", responseTime, resultCount);
        logger.info("Daily batch job completed in {}ms, {} trending videos", responseTime, resultCount);
    }
}