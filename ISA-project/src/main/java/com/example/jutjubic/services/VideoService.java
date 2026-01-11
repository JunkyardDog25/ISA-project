package com.example.jutjubic.services;

import com.example.jutjubic.dto.CreateVideoDto;
import com.example.jutjubic.dto.VideoDto;
import com.example.jutjubic.dto.ViewResponseDto;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.repositories.VideoRepository;
import com.example.jutjubic.utils.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.sql.Time;
import java.util.Optional;
import java.util.UUID;

@Service
public class VideoService {

    private static final int MAX_PAGE_SIZE = 100;

    private final VideoRepository videoRepository;

    public VideoService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    public Video create(VideoDto videoDto) {
        Video video = new Video(
                videoDto.getTitle(),
                videoDto.getDescription(),
                videoDto.getVideoPath(),
                videoDto.getThumbnailPath(),
                videoDto.getThumbnailCompressedPath(),
                videoDto.getFileSize() != null ? videoDto.getFileSize() : 0L,
                videoDto.getDuration() != null ? videoDto.getDuration() : new Time(0),
                videoDto.getTranscoded() != null ? videoDto.getTranscoded() : false,
                videoDto.getScheduledAt(),
                videoDto.getCountry(),
                videoDto.getViewCount() != null ? videoDto.getViewCount() : 0,
                videoDto.getUser()
        );

        return videoRepository.save(video);
    }

    public Video createVideo(CreateVideoDto createVideoDto, User user) {
        Video video = new Video(
                createVideoDto.getTitle(),
                createVideoDto.getDescription(),
                createVideoDto.getVideoPath(),
                createVideoDto.getThumbnailPath(),
                createVideoDto.getThumbnailCompressedPath(),
                createVideoDto.getFileSize() != null ? createVideoDto.getFileSize() : 0L,
                createVideoDto.getDuration() != null ? createVideoDto.getDuration() : new Time(0),
                createVideoDto.getTranscoded() != null ? createVideoDto.getTranscoded() : false,
                createVideoDto.getScheduledAt(),
                createVideoDto.getCountry(),
                0L,
                user
        );

        return videoRepository.save(video);
    }

    public Iterable<Video> getAllVideos() {
        return videoRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public PageResponse<Video> getVideosPaginated(int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Video> videoPage = videoRepository.findAll(pageable);

        return PageResponse.from(videoPage);
    }

    public Video getVideoById(UUID id) {
        Optional<Video> optionalVideo = videoRepository.findVideoById(id);
        return optionalVideo.orElse(null);
    }


    public ViewResponseDto incrementViews(UUID videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        video.setViewCount(video.getViewCount() + 1);
        videoRepository.save(video);

        return new ViewResponseDto(true, video.getViewCount());
    }
}
