package com.example.jutjubic.controllers;

import com.example.jutjubic.services.ThumbnailService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Controller for serving thumbnail images with caching.
 * Thumbnails are cached in memory to improve performance.
 */
@RestController
@RequestMapping("/api/thumbnails")
public class ThumbnailController {
    private static final Logger logger = LoggerFactory.getLogger(ThumbnailController.class);
    
    private final ThumbnailService thumbnailService;
    
    public ThumbnailController(ThumbnailService thumbnailService) {
        this.thumbnailService = thumbnailService;
    }
    
    /**
     * Serves a thumbnail image with caching support.
     * Accepts path as path variable (e.g., /api/thumbnails/thumbnails/thumb1.jpg)
     * 
     * @param path Path to thumbnail (e.g., "thumbnails/thumb1.jpg")
     * @return ResponseEntity with thumbnail image
     */
    @GetMapping("/**")
    public ResponseEntity<?> getThumbnail(HttpServletRequest request) {
        try {
            // Extract path from request URI
            String requestUri = request.getRequestURI();
            String basePath = "/api/thumbnails/";
            String path = requestUri.substring(requestUri.indexOf(basePath) + basePath.length());
            
            // Remove leading slash if present
            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            
            // Security: Prevent path traversal
            if (cleanPath.contains("..") || cleanPath.contains("\\")) {
                logger.warn("Invalid thumbnail path requested: {}", cleanPath);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Invalid thumbnail path");
            }
            
            Resource resource = thumbnailService.getThumbnailResource(cleanPath);
            String contentType = thumbnailService.getContentType(cleanPath);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000") // Cache for 1 year
                    .body(resource);
                    
        } catch (IOException e) {
            logger.error("Error serving thumbnail", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Thumbnail not found");
        } catch (Exception e) {
            logger.error("Unexpected error serving thumbnail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error serving thumbnail");
        }
    }
    
    /**
     * Gets cache statistics (for monitoring/debugging).
     * 
     * @return Cache size
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<?> getCacheStats() {
        int cacheSize = thumbnailService.getCacheSize();
        return ResponseEntity.ok()
                .body("{\"cacheSize\": " + cacheSize + "}");
    }
    
    /**
     * Clears the thumbnail cache (admin operation).
     * 
     * @return Success message
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<?> clearCache() {
        thumbnailService.clearCache();
        return ResponseEntity.ok().body("Cache cleared successfully");
    }
}
