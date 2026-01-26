package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.ViewResponseDto;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.services.UserService;
import com.example.jutjubic.services.VideoService;
import com.example.jutjubic.utils.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
class VideoController {
    private static final Logger logger = LoggerFactory.getLogger(VideoController.class);

    private final VideoService videoService;
    private final UserService userService;

    VideoController(VideoService videoService, UserService userService) {
        this.videoService = videoService;
        this.userService = userService;
    }

    /**
     * Creates a new video transactionally with file upload.
     * The entire operation is wrapped in a transaction that will be rolled back
     * if any error occurs during video creation.
     *
     * @param title Video title
     * @param description Video description
     * @param tags Video tags (comma-separated)
     * @param latitude Video location latitude (optional)
     * @param longitude Video location longitude (optional)
     * @param videoFile Video file (MP4, max 200MB)
     * @param thumbnailFile Thumbnail image file
     * @return ResponseEntity with created Video or error message
     */
    @PostMapping(value = "/create", consumes = "multipart/form-data")
    public ResponseEntity<?> createVideo(
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("tags") String tags,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam("videoFile") MultipartFile videoFile,
            @RequestParam("thumbnailFile") MultipartFile thumbnailFile) {
        try {
            User authenticatedUser = userService.getLoggedUser();

            if (authenticatedUser == null) {
                logger.warn("Unauthorized attempt to create video");
                return ResponseEntity.status(401).body("Unauthorized");
            }

            logger.info("Creating video for user: {} (ID: {})", authenticatedUser.getUsername(), authenticatedUser.getId());

            Video video = videoService.createVideoWithFiles(title, description, tags, latitude, longitude,
                    videoFile, thumbnailFile, authenticatedUser);
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

    /**
     * Get nearby search configuration parameters.
     * Returns the configurable default values for nearby video search.
     *
     * @return Map containing defaultRadius, maxRadius, and defaultUnits
     */
    @GetMapping("/nearby/config")
    public ResponseEntity<java.util.Map<String, Object>> getNearbyConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("defaultRadius", videoService.getDefaultRadiusKm());
        config.put("maxRadius", videoService.getMaxRadiusKm());
        config.put("defaultUnits", videoService.getDefaultUnits());
        return ResponseEntity.ok(config);
    }

    @PutMapping("/{videoId}/views")
    public ResponseEntity<ViewResponseDto> incrementViews(@PathVariable UUID videoId) {
        ViewResponseDto response = videoService.incrementViews(videoId);
        return ResponseEntity.ok(response);
    }

    /**
     * Search for videos near a specified location.
     * Uses spatial indexing with bounding box pre-filtering for optimal performance.
     *
     * @param location Optional location as a "lat, lon" string. If not provided, uses user's stored location or IP approximation.
     * @param radius Search radius. If not provided or <= 0, uses configured default from application.properties.
     * @param units Distance units (km, m, mi). If not provided, uses configured default from application.properties.
     * @param page Page number (0-based)
     * @param size Page size
     * @return Paginated list of videos within the search radius
     */
    @GetMapping("/nearby")
    public ResponseEntity<PageResponse<Video>> searchNearby(
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "radius", required = false, defaultValue = "-1") double radius,
            @RequestParam(value = "units", required = false) String units,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "16") int size
    ) {
        try {
            User authenticatedUser = userService.getLoggedUser();

            PageResponse<Video> videos = videoService.searchNearby(location, radius, units, authenticatedUser, page, size);
            return ResponseEntity.ok(videos);
        } catch (NumberFormatException e) {
            logger.error("Invalid location format", e);
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for nearby search", e);
            return ResponseEntity.badRequest().body(PageResponse.empty());
        } catch (Exception e) {
            logger.error("Error searching nearby videos", e);
            return ResponseEntity.status(500).build();
        }
    }
}