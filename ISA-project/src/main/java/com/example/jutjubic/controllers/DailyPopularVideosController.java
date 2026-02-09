package com.example.jutjubic.controllers;

import com.example.jutjubic.models.DailyPopularVideo;
import com.example.jutjubic.services.DailyPopularVideosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DailyPopularVideosController {
    private static final Logger logger = LoggerFactory.getLogger(DailyPopularVideosController.class);
    private final DailyPopularVideosService dailyPopularVideosService;

    public DailyPopularVideosController(DailyPopularVideosService dailyPopularVideosService) {
        this.dailyPopularVideosService = dailyPopularVideosService;
    }

    @GetMapping("/daily-popular-videos")
    public ResponseEntity<List<DailyPopularVideo>> getDailyPopularVideos() {
        List<DailyPopularVideo> topVideos = dailyPopularVideosService.getAll();
        logger.info("Retrieved top daily popular videos from database.");
        return ResponseEntity.ok(topVideos);
    }
}