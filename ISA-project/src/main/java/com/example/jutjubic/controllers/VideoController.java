package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.VideoDto;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.services.VideoService;
import com.example.jutjubic.utils.PageResponse;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Video> createVideo(@RequestBody VideoDto videoDto) {
        Video video = videoService.create(videoDto);
        return ResponseEntity.ok(video);
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
}
