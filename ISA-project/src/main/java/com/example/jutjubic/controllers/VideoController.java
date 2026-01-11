package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.CreateVideoDto;
import com.example.jutjubic.dto.VideoDto;
import com.example.jutjubic.dto.ViewResponseDto;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.services.VideoService;
import com.example.jutjubic.utils.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
class VideoController {
    private static final Logger logger = LoggerFactory.getLogger(VideoController.class);
    
    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    /**
     * Creates a new video transactionally.
     * The entire operation is wrapped in a transaction that will be rolled back
     * if any error occurs during video creation.
     * 
     * @param createVideoDto DTO containing video creation data
     * @return ResponseEntity with created Video or error message
     */
    @PostMapping("/create")
    public ResponseEntity<?> createVideo(@RequestBody CreateVideoDto createVideoDto) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User)) {
                logger.warn("Unauthorized attempt to create video");
                return ResponseEntity.status(401).body("Unauthorized");
            }
            
            User authenticatedUser = (User) authentication.getPrincipal();
            logger.info("Creating video for user: {} (ID: {})", authenticatedUser.getUsername(), authenticatedUser.getId());
            
            Video video = videoService.createVideo(createVideoDto, authenticatedUser);
            logger.info("Video created successfully with ID: {}", video.getId());
            
            return ResponseEntity.ok(video);
        } catch (IllegalArgumentException e) {
            logger.error("Validation error while creating video: {}", e.getMessage());
            return ResponseEntity.status(400).body("Error creating video: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating video", e);
            return ResponseEntity.status(500).body("Error creating video: " + e.getMessage());
        }
    }

    @GetMapping("/")
    public ResponseEntity<PageResponse<Video>> getAllVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "16") int size
    ) {
        PageResponse<Video> videos = videoService.getVideosPaginated(page, size);
        return ResponseEntity.ok(videos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Video> getVideoById(@PathVariable UUID id) {
        Video video = videoService.getVideoById(id);
        return ResponseEntity.ok(video);
    }


    @PutMapping("/{videoId}/views")
    public ResponseEntity<ViewResponseDto> incrementViews(@PathVariable UUID videoId) {
        ViewResponseDto response = videoService.incrementViews(videoId);
        return ResponseEntity.ok(response);
    }
}
