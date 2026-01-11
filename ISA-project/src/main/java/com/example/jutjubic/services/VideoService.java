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
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.util.Optional;
import java.util.UUID;

@Service
public class VideoService {

    private static final int MAX_PAGE_SIZE = 100;

    private final VideoRepository videoRepository;
    private final UserService userService;

    public VideoService(VideoRepository videoRepository, UserService userService) {
        this.videoRepository = videoRepository;
        this.userService = userService;
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
                null, // tags - not in VideoDto, can be null
                videoDto.getViewCount() != null ? videoDto.getViewCount() : 0,
                videoDto.getUser()
        );

        return videoRepository.save(video);
    }

    private static final long MAX_VIDEO_SIZE = 200L * 1024 * 1024; // 200MB in bytes

    @Transactional
    public Video createVideo(CreateVideoDto createVideoDto, User user) {
        System.out.println("=== VideoService.createVideo START ===");
        System.out.println("Title: " + createVideoDto.getTitle());
        System.out.println("User ID: " + user.getId());
        
        // Validate file size
        if (createVideoDto.getFileSize() != null && createVideoDto.getFileSize() > MAX_VIDEO_SIZE) {
            throw new IllegalArgumentException("Video file size exceeds maximum allowed size of 200MB");
        }

        // Load user from database to ensure it's a managed entity
        System.out.println("Loading user from database...");
        User managedUser = userService.getUserById(user.getId());
        System.out.println("User loaded: " + managedUser.getUsername());

        System.out.println("Creating Video object...");
        Video video = new Video(
                createVideoDto.getTitle(),
                createVideoDto.getDescription(),
                createVideoDto.getVideoPath(),
                createVideoDto.getThumbnailPath(),
                null, // thumbnailCompressedPath - not needed for creation
                createVideoDto.getFileSize() != null ? createVideoDto.getFileSize() : 0L,
                new Time(0), // duration - will be set later if needed
                false, // transcoded - default false
                null, // scheduledAt - not needed for immediate posting
                createVideoDto.getCountry(),
                createVideoDto.getTags(),
                0L, // viewCount - starts at 0
                managedUser
        );
        
        System.out.println("Video object created - ID before save: " + video.getId());
        System.out.println("Video title: " + video.getTitle());
        System.out.println("Video videoPath: " + video.getVideoPath());
        System.out.println("Video creator ID: " + (video.getCreator() != null ? video.getCreator().getId() : "NULL"));
        
        System.out.println("Calling videoRepository.save()...");
        Video savedVideo = videoRepository.save(video);
        System.out.println("Video saved - ID after save: " + savedVideo.getId());
        
        System.out.println("Calling videoRepository.flush()...");
        videoRepository.flush();
        System.out.println("After flush - ID: " + savedVideo.getId());
        
        // Verify the video was actually saved
        System.out.println("Verifying video exists in database...");
        Video verifyVideo = videoRepository.findById(savedVideo.getId()).orElse(null);
        if (verifyVideo != null) {
            System.out.println("✓ Video verified in database - ID: " + verifyVideo.getId());
        } else {
            System.out.println("✗ ERROR: Video NOT found in database after save!");
        }
        
        System.out.println("=== VideoService.createVideo SUCCESS ===");
        return savedVideo;
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
