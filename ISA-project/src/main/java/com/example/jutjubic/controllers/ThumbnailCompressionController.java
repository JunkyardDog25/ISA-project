package com.example.jutjubic.controllers;

import com.example.jutjubic.models.User;
import com.example.jutjubic.services.ThumbnailCompressionService;
import com.example.jutjubic.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST kontroler za administraciju kompresije thumbnail slika.
 *
 * Omogućava manuelno pokretanje kompresije thumbnail-a bez čekanja
 * na automatski scheduler koji se pokreće svaki dan u dva ujutru.
 */
@RestController
@RequestMapping("/api/admin/thumbnails")
public class ThumbnailCompressionController {

    private final ThumbnailCompressionService thumbnailCompressionService;
    private final UserService userService;

    public ThumbnailCompressionController(ThumbnailCompressionService thumbnailCompressionService,
                                          UserService userService) {
        this.thumbnailCompressionService = thumbnailCompressionService;
        this.userService = userService;
    }

    /**
     * Manuelno pokreće kompresiju svih nekompresovanih thumbnail-a
     */
    @PostMapping("/compress-all")
    public ResponseEntity<Map<String, Object>> compressAllThumbnails() {
        User loggedUser = userService.getLoggedUser();
        if (loggedUser == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Authentication required");
            return ResponseEntity.status(401).body(response);
        }

        int compressedCount = thumbnailCompressionService.compressAllUncompressedThumbnails();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Thumbnail compression completed");
        response.put("compressedCount", compressedCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Kompresuje thumbnail za pojedinačni video.
     */
    @PostMapping("/compress/{videoId}")
    public ResponseEntity<Map<String, Object>> compressThumbnailForVideo(@PathVariable UUID videoId) {
        User loggedUser = userService.getLoggedUser();
        if (loggedUser == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Authentication required");
            return ResponseEntity.status(401).body(response);
        }

        boolean success = thumbnailCompressionService.compressThumbnailForVideo(videoId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("videoId", videoId.toString());

        if (success) {
            response.put("message", "Thumbnail compressed successfully");
        } else {
            response.put("message", "Failed to compress thumbnail");
        }

        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    /**
     * Pokreće scheduler ručno za kompresiju (ista logika kao automatski scheduler).
     */
    @PostMapping("/run-scheduled-job")
    public ResponseEntity<Map<String, Object>> runScheduledJob() {
        User loggedUser = userService.getLoggedUser();
        if (loggedUser == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Authentication required");
            return ResponseEntity.status(401).body(response);
        }

        try {
            thumbnailCompressionService.compressOldThumbnails();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Scheduled compression job executed successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to execute compression job: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }
}


