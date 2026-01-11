package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.CreateVideoDto;
import com.example.jutjubic.dto.VideoDto;
import com.example.jutjubic.dto.ViewResponseDto;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.services.VideoService;
import com.example.jutjubic.utils.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
class VideoController {
    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createVideo(@RequestBody CreateVideoDto createVideoDto) {
        System.out.println("========================================");
        System.out.println("POST /api/videos/create - CALLED");
        System.out.println("Title: " + (createVideoDto != null ? createVideoDto.getTitle() : "NULL"));
        System.out.println("========================================");
        
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            System.out.println("Authentication: " + (authentication != null ? "NOT NULL" : "NULL"));
            
            if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User)) {
                System.out.println("Authentication failed - returning 401");
                return ResponseEntity.status(401).body("Unauthorized");
            }
            
            User authenticatedUser = (User) authentication.getPrincipal();
            System.out.println("User authenticated: " + authenticatedUser.getUsername() + " (ID: " + authenticatedUser.getId() + ")");
            
            System.out.println("Calling videoService.createVideo()...");
            Video video = videoService.createVideo(createVideoDto, authenticatedUser);
            System.out.println("Video created with ID: " + video.getId());
            System.out.println("Returning video to client...");
            
            return ResponseEntity.ok(video);
        } catch (Exception e) {
            System.err.println("ERROR in createVideo controller: " + e.getMessage());
            e.printStackTrace();
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
