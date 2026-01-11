package com.example.jutjubic.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for serving and caching thumbnail images.
 * Thumbnails are cached in memory to avoid reading from file system on every request.
 */
@Service
public class ThumbnailService {
    private static final Logger logger = LoggerFactory.getLogger(ThumbnailService.class);
    
    private final ConcurrentHashMap<String, byte[]> thumbnailCache = new ConcurrentHashMap<>();
    
    /**
     * Gets thumbnail image as byte array, using cache if available.
     * 
     * @param thumbnailPath Path to thumbnail (e.g., "thumbnails/thumb1.jpg")
     * @return Byte array containing the thumbnail image
     * @throws IOException if thumbnail cannot be read
     */
    public byte[] getThumbnail(String thumbnailPath) throws IOException {
        // Check cache first
        byte[] cached = thumbnailCache.get(thumbnailPath);
        if (cached != null) {
            logger.debug("Thumbnail served from cache: {}", thumbnailPath);
            return cached;
        }
        
        // Load from classpath (works in both development and JAR)
        logger.debug("Loading thumbnail from classpath: {}", thumbnailPath);
        Resource resource = new ClassPathResource("static/" + thumbnailPath);
        
        if (!resource.exists()) {
            logger.warn("Thumbnail not found: {}", thumbnailPath);
            throw new IOException("Thumbnail not found: " + thumbnailPath);
        }
        
        byte[] thumbnailBytes;
        try (InputStream inputStream = resource.getInputStream()) {
            thumbnailBytes = inputStream.readAllBytes();
        }
        
        // Cache the thumbnail
        thumbnailCache.put(thumbnailPath, thumbnailBytes);
        logger.info("Thumbnail cached: {} ({} bytes)", thumbnailPath, thumbnailBytes.length);
        
        return thumbnailBytes;
    }
    
    /**
     * Gets thumbnail as Spring Resource for HTTP response.
     * 
     * @param thumbnailPath Path to thumbnail (e.g., "thumbnails/thumb1.jpg")
     * @return Resource containing the thumbnail
     * @throws IOException if thumbnail cannot be read
     */
    public Resource getThumbnailResource(String thumbnailPath) throws IOException {
        // Verify thumbnail exists and is cached (this will cache it if not already cached)
        getThumbnail(thumbnailPath);
        
        // Return resource from classpath
        return new ClassPathResource("static/" + thumbnailPath);
    }
    
    /**
     * Gets the content type for a thumbnail based on its file extension.
     * 
     * @param thumbnailPath Path to thumbnail
     * @return Content type (e.g., "image/jpeg", "image/png")
     */
    public String getContentType(String thumbnailPath) {
        String lowerPath = thumbnailPath.toLowerCase();
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerPath.endsWith(".png")) {
            return "image/png";
        } else if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerPath.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg"; // Default
    }
    
    /**
     * Clears the thumbnail cache.
     * Useful for cache invalidation when thumbnails are updated.
     */
    public void clearCache() {
        thumbnailCache.clear();
        logger.info("Thumbnail cache cleared");
    }
    
    /**
     * Removes a specific thumbnail from cache.
     * 
     * @param thumbnailPath Path to thumbnail to remove from cache
     */
    public void evictFromCache(String thumbnailPath) {
        thumbnailCache.remove(thumbnailPath);
        logger.debug("Thumbnail evicted from cache: {}", thumbnailPath);
    }
    
    /**
     * Gets the current cache size.
     * 
     * @return Number of thumbnails in cache
     */
    public int getCacheSize() {
        return thumbnailCache.size();
    }
}
