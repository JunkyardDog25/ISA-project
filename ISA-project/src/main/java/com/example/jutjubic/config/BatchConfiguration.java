package com.example.jutjubic.config;

import com.example.jutjubic.models.DailyPopularVideo;
import com.example.jutjubic.models.VideoView;
import com.example.jutjubic.repositories.DailyPopularVideoRepository;
import com.example.jutjubic.repositories.VideoViewRepository;
import com.example.jutjubic.services.CommentService;
import com.example.jutjubic.services.LikeService;
import com.example.jutjubic.utils.PopularityCalculator;
import lombok.RequiredArgsConstructor;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class BatchConfiguration {

    private static final int CHUNK_SIZE = 10;
    private static final int PAGE_SIZE = 100;
    private static final int COMMENT_WEIGHT = 2;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final VideoViewRepository videoViewRepository;
    private final DailyPopularVideoRepository dailyPopularVideoRepository;
    private final CommentService commentService;
    private final LikeService likeService;

    @Bean
    public RepositoryItemReader<VideoView> reader() {
        return new RepositoryItemReaderBuilder<VideoView>()
                .name("videoViewItemReader")
                .repository(videoViewRepository)
                .methodName("findAllWithVideo")
                .pageSize(PAGE_SIZE)
                .sorts(Map.of("createdAt", Sort.Direction.DESC))
                .build();
    }

    @Bean
    public ItemProcessor<VideoView, DailyPopularVideo> processor() {
        return this::processVideoView;
    }

    @Bean
    public RepositoryItemWriter<DailyPopularVideo> writer() {
        return new RepositoryItemWriterBuilder<DailyPopularVideo>()
                .repository(dailyPopularVideoRepository)
                .methodName("save")
                .build();
    }

    @Bean
    public Step step() {
        return new StepBuilder("videoView-to-dailyPopular-step", jobRepository)
                .<VideoView, DailyPopularVideo>chunk(CHUNK_SIZE)
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
                .start(step())
                .build();
    }

    private DailyPopularVideo processVideoView(VideoView videoView) {
        if (videoView == null) {
            return null;
        }

        UUID videoId = videoView.getVideo().getId();
        long viewCount = videoView.getVideo().getViewCount();

        double score = calculatePopularityScore(videoView, videoId, viewCount);

        return createDailyPopularVideo(videoView, score);
    }

    private double calculatePopularityScore(VideoView videoView, UUID videoId, long viewCount) {
        if (viewCount == 0) {
            return 0.0;
        }

        long commentCount = commentService.getCommentCount(videoId);
        long likeCount = likeService.getLikeCount(videoId);
        long engagementBonus = likeCount + commentCount * COMMENT_WEIGHT;

        return PopularityCalculator.calculateScore(videoView) + engagementBonus;
    }

    private DailyPopularVideo createDailyPopularVideo(VideoView videoView, double score) {
        DailyPopularVideo dailyPopularVideo = new DailyPopularVideo();
        dailyPopularVideo.setVideo(videoView.getVideo());
        dailyPopularVideo.setExecutionDate(LocalDate.now());
        dailyPopularVideo.setPopularityScore(score);
        return dailyPopularVideo;
    }
}
