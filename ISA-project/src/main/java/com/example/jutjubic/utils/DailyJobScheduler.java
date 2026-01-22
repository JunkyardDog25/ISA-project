package com.example.jutjubic.utils;

import com.example.jutjubic.repositories.DailyPopularVideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyJobScheduler {
    private static final Logger logger = LoggerFactory.getLogger(DailyJobScheduler.class);

    private final JobOperator jobOperator;
    private final Job job;
    private final DailyPopularVideoRepository dailyPopularVideoRepository;

    public DailyJobScheduler(JobOperator jobOperator, Job job, DailyPopularVideoRepository dailyPopularVideoRepository) {
        this.jobOperator = jobOperator;
        this.job = job;
        this.dailyPopularVideoRepository = dailyPopularVideoRepository;
    }

    @Scheduled(cron = "0 0 12 * * *")
    public void runDailyJob() {
        logger.info("Starting scheduled daily batch job...");
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("jobName", job.getName())
                    .toJobParameters();
            jobOperator.start(job, params);
        } catch (Exception e) {
            logger.error("Error executing scheduled job", e);
        }
        dailyPopularVideoRepository.keepOnlyTop3();
    }
}