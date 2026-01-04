package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.LikeDto;
import com.example.jutjubic.dto.LikeResponseDto;
import com.example.jutjubic.services.LikeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping("/{videoId}/like")
    public ResponseEntity<LikeResponseDto> toggleLike(@RequestBody LikeDto likeDto, @PathVariable UUID videoId) {
        LikeResponseDto response = likeService.toggleLike(videoId, likeDto.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{videoId}/like")
    public ResponseEntity<LikeResponseDto> getLikeStatus(
            @PathVariable UUID videoId,
            @RequestParam UUID userId
    ) {
        LikeResponseDto response = likeService.getLikeStatus(videoId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{videoId}/likes/count")
    public ResponseEntity<Long> getLikeCount(@PathVariable UUID videoId) {
        long count = likeService.getLikeCount(videoId);
        return ResponseEntity.ok(count);
    }
}

