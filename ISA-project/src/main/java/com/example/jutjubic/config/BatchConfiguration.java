package com.example.jutjubic.config;

import com.example.jutjubic.dto.CommentDto;
import com.example.jutjubic.models.DailyPopularVideo;
import com.example.jutjubic.models.VideoView;
import com.example.jutjubic.repositories.DailyPopularVideoRepository;
import com.example.jutjubic.repositories.VideoViewRepository;
import com.example.jutjubic.services.CommentService;
import com.example.jutjubic.services.LikeService;
import com.example.jutjubic.utils.PopularityCalculator;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.data.RepositoryItemReader;
import org.springframework.batch.infrastructure.item.data.RepositoryItemWriter;
import org.springframework.batch.infrastructure.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.infrastructure.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Configuration
public class BatchConfiguration {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final VideoViewRepository videoViewRepository;
    private final DailyPopularVideoRepository dailyPopularVideoRepository;
    private final CommentService commentService;
    private final LikeService likeService;

    public BatchConfiguration(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              VideoViewRepository videoViewRepository,
                              DailyPopularVideoRepository dailyPopularVideoRepository,
                              CommentService commentService,
                              LikeService likeService) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.videoViewRepository = videoViewRepository;
        this.dailyPopularVideoRepository = dailyPopularVideoRepository;
        this.commentService = commentService;
        this.likeService = likeService;
    }

    @Bean
    public RepositoryItemReader<VideoView> reader() {
        return new RepositoryItemReaderBuilder<VideoView>()
                .name("videoViewItemReader")
                .repository(videoViewRepository)
                .methodName("findAllWithVideo")
                .pageSize(100)
                .sorts(Map.of("createdAt", Sort.Direction.DESC))
                .build();
    }

    @Bean
    public ItemProcessor<VideoView, DailyPopularVideo> processor() {
        return videoView -> {
            if (videoView == null) {
                return null;
            }
            long viewCount = videoView.getVideo().getViewCount();
            if (viewCount == 0) {
                DailyPopularVideo dailyPopularVideo = new DailyPopularVideo();
                dailyPopularVideo.setVideo(videoView.getVideo());
                dailyPopularVideo.setExecutionDate(LocalDate.now());
                dailyPopularVideo.setPopularityScore(0.0);
                return dailyPopularVideo;
            }

            long commentCount = commentService.getCommentCount(videoView.getVideo().getId());
            long likeCount = likeService.getLikeCount(videoView.getVideo().getId());

            long engagement_rate = likeCount + commentCount * 2;

            Double score = PopularityCalculator.calculateScore(videoView);
            score = score + engagement_rate;

            DailyPopularVideo dailyPopularVideo = new DailyPopularVideo();
            dailyPopularVideo.setVideo(videoView.getVideo());
            dailyPopularVideo.setExecutionDate(LocalDate.now());
            dailyPopularVideo.setPopularityScore(score);
            return dailyPopularVideo;
        };
    }

    @Bean
    public RepositoryItemWriter<DailyPopularVideo> writer() {
        return new RepositoryItemWriterBuilder<DailyPopularVideo>()
                .repository(dailyPopularVideoRepository)
                .methodName("save")
                .build();
    }

    @Bean
    public Step step(PlatformTransactionManager transactionManager) {
        return new StepBuilder("videoView-to-dailyPopular-step", jobRepository)
                .<VideoView, DailyPopularVideo>chunk(10)
                .reader(reader())
                .processor(processor())
                .writer(writer())
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job job() {
        return new JobBuilder("videoView-to-dailyPopular", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(step(transactionManager))
                .build();
    }
}
