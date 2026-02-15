package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.MqBenchResult;
import com.example.jutjubic.messaging.UploadEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/mq")
public class MqBenchController {

    private final UploadEventPublisher publisher;

    public MqBenchController(UploadEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/bench")
    public MqBenchResult bench(@RequestParam(defaultValue = "50") int count) {
        if (count < 1) count = 1;
        if (count > 5000) count = 5000;

        return publisher.benchmark(count);
    }

    /**
     * Test endpoint za direktno slanje poruke na upload queue-ove.
     * Pozovite: POST /api/mq/test
     */
    @PostMapping("/test")
    public String testUploadQueues() {
        try {
            UUID testId = UUID.randomUUID();
            publisher.publishBothWithDataDirect(testId, "Test Video", 12345L, "testuser", LocalDateTime.now());
            return "SUCCESS: Test message sent to upload.queue.json and upload.queue.pb with videoId=" + testId;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
