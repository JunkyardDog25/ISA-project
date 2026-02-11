package com.example.jutjubic.services;

import com.example.jutjubic.models.Video;
import com.example.jutjubic.repositories.VideoRepository;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Servis za periodičnu kompresiju thumbnail slika.
 */
@Service
public class ThumbnailCompressionService {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailCompressionService.class);

    private static final String MEDIA_PATH = "media/";
    private static final String THUMBNAILS_DIR = "thumbnails";
    private static final String COMPRESSED_DIR = "compressed";
    private static final String COMPRESSED_PREFIX = "compressed_";

    /**
     * (50% kvaliteta)
     */
    @Value("${thumbnail.compression.quality:0.5}")
    private double compressionQuality;

    /**
     * Broj dana starosti thumbnail-a za kompresiju. 30 dana (mesec dana)
     */
    @Value("${thumbnail.compression.age-days:30}")
    private int compressionAgeDays;

    @Value("${thumbnail.compression.max-width:640}")
    private int maxWidth;

    @Value("${thumbnail.compression.max-height:480}")
    private int maxHeight;

    private final VideoRepository videoRepository;

    public ThumbnailCompressionService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;

        // Ensure compressed directory exists
        try {
            Path compressedPath = Paths.get(MEDIA_PATH, THUMBNAILS_DIR, COMPRESSED_DIR);
            Files.createDirectories(compressedPath);
            logger.info("Compressed thumbnails directory initialized: {}", compressedPath);
        } catch (IOException e) {
            logger.error("Failed to create compressed thumbnails directory", e);
        }
    }

    /**
     * Pronalazi sve nekompresovane thumbnail slike starije od mesec dana i kompresuje ih.
     * Cron izraz: "0 0 2 * * ?" - svaki dan u 02:00:00
     */
    @Scheduled(cron = "${thumbnail.compression.cron:0 0 2 * * ?}")
    @Transactional
    public void compressOldThumbnails() {
        logger.info("Starting scheduled thumbnail compression job");

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(compressionAgeDays);
        logger.info("Looking for uncompressed thumbnails older than: {}", cutoffDate);

        List<Video> videosToCompress = videoRepository.findVideosWithUncompressedThumbnails(cutoffDate);

        if (videosToCompress.isEmpty()) {
            logger.info("No thumbnails found for compression");
            return;
        }

        logger.info("Found {} thumbnails to compress", videosToCompress.size());

        int successCount = 0;
        int failCount = 0;

        for (Video video : videosToCompress) {
            try {
                String compressedPath = compressThumbnail(video);
                if (compressedPath != null) {
                    video.setThumbnailCompressedPath(compressedPath);
                    videoRepository.save(video);
                    successCount++;
                    logger.debug("Successfully compressed thumbnail for video: {}", video.getId());
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                logger.error("Failed to compress thumbnail for video {}: {}", video.getId(), e.getMessage());
            }
        }

        logger.info("Thumbnail compression job completed. Success: {}, Failed: {}", successCount, failCount);
    }

    /**
     * Kompresuje thumbnail sliku za dati video
     */
    public String compressThumbnail(Video video) {
        String originalPath = video.getThumbnailPath();

        if (originalPath == null || originalPath.isEmpty()) {
            logger.warn("Video {} has no thumbnail path", video.getId());
            return null;
        }

        String relativePath = originalPath.startsWith("/") ? originalPath.substring(1) : originalPath;
        Path sourceFile = Paths.get(relativePath);

        if (!Files.exists(sourceFile)) {
            logger.warn("Thumbnail file not found: {}", sourceFile);
            return null;
        }

        try {
            // Generate compressed filename
            String originalFilename = sourceFile.getFileName().toString();
            String compressedFilename = COMPRESSED_PREFIX + originalFilename;

            Path compressedDir = Paths.get(MEDIA_PATH, THUMBNAILS_DIR, COMPRESSED_DIR);
            Path destinationFile = compressedDir.resolve(compressedFilename);

            // Get original file size for logging
            long originalSize = Files.size(sourceFile);

            // Compress the image using Thumbnailator
            Thumbnails.of(sourceFile.toFile())
                    .size(maxWidth, maxHeight)
                    .outputQuality(compressionQuality)
                    .toFile(destinationFile.toFile());

            // Get compressed file size for logging
            long compressedSize = Files.size(destinationFile);
            double compressionRatio = (1.0 - (double) compressedSize / originalSize) * 100;

            logger.info("Compressed thumbnail: {} -> {} (reduced by {}%)",
                    originalFilename, compressedFilename, String.format("%.1f", compressionRatio));

            // Return web-accessible path
            return "/" + MEDIA_PATH + THUMBNAILS_DIR + "/" + COMPRESSED_DIR + "/" + compressedFilename;

        } catch (IOException e) {
            logger.error("Error compressing thumbnail {}: {}", originalPath, e.getMessage());
            return null;
        }
    }

    /**
     * Manuelno pokreće kompresiju thumbnail-a za SVE nekompresovane slike (bez obzira na starost).
     */
    @Transactional
    public int compressAllUncompressedThumbnails() {
        logger.info("Starting manual thumbnail compression for ALL uncompressed thumbnails");

        List<Video> videosToCompress = videoRepository.findAllVideosWithUncompressedThumbnails();

        int successCount = 0;

        for (Video video : videosToCompress) {
            try {
                String compressedPath = compressThumbnail(video);
                if (compressedPath != null) {
                    video.setThumbnailCompressedPath(compressedPath);
                    videoRepository.save(video);
                    successCount++;
                }
            } catch (Exception e) {
                logger.error("Failed to compress thumbnail for video {}: {}", video.getId(), e.getMessage());
            }
        }

        logger.info("Manual compression completed. Compressed {} thumbnails", successCount);
        return successCount;
    }

    /**
     * Kompresuje thumbnail za pojedinačni video (po ID-u).
     */
    @Transactional
    public boolean compressThumbnailForVideo(java.util.UUID videoId) {
        Video video = videoRepository.findVideoById(videoId).orElse(null);

        if (video == null) {
            logger.warn("Video not found: {}", videoId);
            return false;
        }

        if (video.getThumbnailCompressedPath() != null) {
            logger.info("Thumbnail already compressed for video: {}", videoId);
            return true;
        }

        String compressedPath = compressThumbnail(video);
        if (compressedPath != null) {
            video.setThumbnailCompressedPath(compressedPath);
            videoRepository.save(video);
            return true;
        }

        return false;
    }
}



