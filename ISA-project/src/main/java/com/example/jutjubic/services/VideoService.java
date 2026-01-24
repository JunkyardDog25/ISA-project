package com.example.jutjubic.services;

import com.example.jutjubic.dto.CreateVideoDto;
import com.example.jutjubic.dto.VideoDto;
import com.example.jutjubic.dto.VideoViewDto;
import com.example.jutjubic.dto.ViewResponseDto;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.models.VideoView;
import com.example.jutjubic.repositories.VideoRepository;
import com.example.jutjubic.repositories.VideoViewRepository;
import com.example.jutjubic.utils.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Time;
import java.util.Optional;
import java.util.UUID;

@Service
public class VideoService {
    private static final Logger logger = LoggerFactory.getLogger(VideoService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_VIDEO_SIZE = 200L * 1024 * 1024; // 200MB in bytes
    private static final String MEDIA_PATH = "media/";
    private static final String VIDEOS_DIR = "videos";
    private static final String THUMBNAILS_DIR = "thumbnails";

    // Configurable nearby search parameters
    @Value("${nearby.default-radius-km:5.0}")
    private double defaultRadiusKm;

    @Value("${nearby.max-radius-km:100.0}")
    private double maxRadiusKm;

    @Value("${nearby.default-units:km}")
    private String defaultUnits;

    private final VideoRepository videoRepository;
    private final VideoViewRepository videoViewRepository;
    private final UserService userService;
    private final PerformanceMetricsService performanceMetricsService;

    public VideoService(VideoRepository videoRepository, VideoViewRepository videoViewRepository,
                       UserService userService, PerformanceMetricsService performanceMetricsService) {
        this.videoRepository = videoRepository;
        this.videoViewRepository = videoViewRepository;
        this.userService = userService;
        this.performanceMetricsService = performanceMetricsService;

        // Ensure directories exist
        try {
            Path videosPath = Paths.get(MEDIA_PATH, VIDEOS_DIR);
            Path thumbnailsPath = Paths.get(MEDIA_PATH, THUMBNAILS_DIR);
            Files.createDirectories(videosPath);
            Files.createDirectories(thumbnailsPath);
            logger.info("Video and thumbnail directories initialized");
        } catch (IOException e) {
            logger.error("Failed to create directories", e);
        }
    }

    public Video create(VideoDto videoDto) {
        Video video = new Video(
                videoDto.getTitle(),
                videoDto.getDescription(),
                videoDto.getVideoPath(),
                videoDto.getThumbnailPath(),
                videoDto.getThumbnailCompressedPath(),
                videoDto.getFileSize() != null ? videoDto.getFileSize() : 0L,
                videoDto.getDuration() != null ? videoDto.getDuration() : new Time(0),
                videoDto.getTranscoded() != null ? videoDto.getTranscoded() : false,
                videoDto.getScheduledAt(),
                null, // tags - not in VideoDto, can be null
                videoDto.getViewCount() != null ? videoDto.getViewCount() : 0,
                videoDto.getUser()
        );

        return videoRepository.save(video);
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
     * @param user Authenticated user creating the video
     * @return Created Video entity
     * @throws IllegalArgumentException if validation fails
     * @throws IOException if file saving fails
     * @throws RuntimeException if user is not found or any other error occurs
     */
    @Transactional(rollbackFor = {Exception.class})
    public Video createVideoWithFiles(String title, String description, String tags,
                                      Double latitude, Double longitude,
                                      MultipartFile videoFile, MultipartFile thumbnailFile,
                                      User user) throws IOException {
        logger.debug("Starting transactional video creation with files for user: {}", user.getId());

        // Create DTO from form data and files
        CreateVideoDto createVideoDto = new CreateVideoDto();
        createVideoDto.setTitle(title);
        createVideoDto.setDescription(description);
        createVideoDto.setTags(tags);
        createVideoDto.setLatitude(latitude);
        createVideoDto.setLongitude(longitude);
        createVideoDto.setFileSize(videoFile.getSize());

        // Save files and set paths
        String videoPath = saveVideoFile(videoFile);
        String thumbnailPath = saveThumbnailFile(thumbnailFile);

        createVideoDto.setVideoPath(videoPath);
        createVideoDto.setThumbnailPath(thumbnailPath);

        return createVideo(createVideoDto, user);
    }

    /**
     * Creates a new video transactionally.
     * If any error occurs during the process, the entire transaction will be rolled back.
     *
     * @param createVideoDto DTO containing video creation data
     * @param user Authenticated user creating the video
     * @return Saved Video entity
     * @throws IllegalArgumentException if file size exceeds maximum allowed size
     * @throws RuntimeException if user is not found or any other error occurs
     */
    @Transactional(rollbackFor = {Exception.class})
    public Video createVideo(CreateVideoDto createVideoDto, User user) {
        logger.debug("Starting transactional video creation for user: {}", user.getId());

        // Validate file size
        if (createVideoDto.getFileSize() != null && createVideoDto.getFileSize() > MAX_VIDEO_SIZE) {
            logger.warn("Video file size validation failed: {} bytes exceeds maximum of {} bytes",
                    createVideoDto.getFileSize(), MAX_VIDEO_SIZE);
            throw new IllegalArgumentException("Video file size exceeds maximum allowed size of 200MB");
        }

        // Load user from database to ensure it's a managed entity within the transaction
        User managedUser = userService.getUserById(user.getId());
        if (managedUser == null) {
            logger.error("User not found with id: {}", user.getId());
            throw new RuntimeException("User not found with id: " + user.getId());
        }

        // Create Video entity
        Video video = new Video(
                createVideoDto.getTitle(),
                createVideoDto.getDescription(),
                createVideoDto.getVideoPath(),
                createVideoDto.getThumbnailPath(),
                null, // thumbnailCompressedPath - not needed for creation
                createVideoDto.getFileSize() != null ? createVideoDto.getFileSize() : 0L,
                createVideoDto.getDuration() != null ? createVideoDto.getDuration() : new Time(0), // duration from extracted metadata
                false, // transcoded - default false
                null, // scheduledAt - not needed for immediate posting
                createVideoDto.getTags(),
                0L, // viewCount - starts at 0
                managedUser
        );

        // Set location if provided
        if (createVideoDto.getLatitude() != null && createVideoDto.getLongitude() != null) {
            video.setLatitude(createVideoDto.getLatitude());
            video.setLongitude(createVideoDto.getLongitude());
        }

        // Save video - this will be committed when transaction completes successfully
        Video savedVideo = videoRepository.save(video);
        logger.debug("Video entity saved with ID: {}", savedVideo.getId());

        // Flush to ensure immediate persistence within transaction
        videoRepository.flush();
        logger.debug("Transaction flushed - video will be committed on method completion");

        return savedVideo;
    }

    public Iterable<Video> getAllVideos() {
        return videoRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public PageResponse<Video> getVideosPaginated(int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Video> videoPage = videoRepository.findAll(pageable);
        videoPage.getContent().forEach(video -> video.setViewCount(
                videoViewRepository.countVideoViewByVideo_Id(video.getId())
        ));

        return PageResponse.from(videoPage);
    }

    public Video getVideoById(UUID id) {
        Optional<Video> optionalVideo = videoRepository.findVideoById(id);
        return optionalVideo.orElse(null);
    }

    /**
     * Dobija paginiranu listu video objava za datog korisnika.
     * Sortira po vremenu kreiranja (najnovije prvo).
     */
    public PageResponse<Video> getVideosByUserId(UUID userId, int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Video> videoPage = videoRepository.findByCreatorId(userId, pageable);

        return PageResponse.from(videoPage);
    }

    @Transactional
    public ViewResponseDto incrementViews(UUID videoId) {
        VideoViewDto videoViewDto = new VideoViewDto(
                videoRepository.findVideoById(videoId).get(),
                userService.getLoggedUser()
        );
        VideoView videoView = new VideoView(videoViewDto);
        videoViewRepository.save(videoView);

        int videoViews = videoViewRepository.countVideoViewByVideo_Id(videoId);

        Video video = videoRepository.findVideoById(videoId).get();
        video.setViewCount(videoViews);
        videoRepository.save(video);

        return new ViewResponseDto(true, videoViews);
    }

    /**
     * Saves uploaded video file to the static resources directory.
     *
     * @param videoFile MultipartFile containing the video
     * @return Web-accessible path to the saved video file (e.g., "/media/videos/video_123.mp4")
     * @throws IOException if file cannot be saved
     */
    public String saveVideoFile(MultipartFile videoFile) throws IOException {
        if (videoFile == null || videoFile.isEmpty()) {
            throw new IllegalArgumentException("Video file is required");
        }

        // Generate unique filename to avoid conflicts
        String originalFilename = videoFile.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
            ? originalFilename.substring(originalFilename.lastIndexOf("."))
            : ".mp4";
        String filename = "video_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

        Path videosPath = Paths.get(MEDIA_PATH, VIDEOS_DIR);
        Path filePath = videosPath.resolve(filename);

        Files.copy(videoFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Video file saved: {}", filePath);

        // Return the web-accessible path so it can be saved in DB and served via /media/** mapping
        return "media/" + VIDEOS_DIR + "/" + filename;
    }

    /**
     * Saves uploaded thumbnail file to the static resources directory.
     *
     * @param thumbnailFile MultipartFile containing the thumbnail image
     * @return Web-accessible path to the saved thumbnail file (e.g., "/media/thumbnails/thumb_123.jpg")
     * @throws IOException if file cannot be saved
     */
    public String saveThumbnailFile(MultipartFile thumbnailFile) throws IOException {
        if (thumbnailFile == null || thumbnailFile.isEmpty()) {
            throw new IllegalArgumentException("Thumbnail file is required");
        }

        // Generate unique filename to avoid conflicts
        String originalFilename = thumbnailFile.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
            ? originalFilename.substring(originalFilename.lastIndexOf("."))
            : ".jpg";
        String filename = "thumb_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

        Path thumbnailsPath = Paths.get(MEDIA_PATH, THUMBNAILS_DIR);
        Path filePath = thumbnailsPath.resolve(filename);

        Files.copy(thumbnailFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Thumbnail file saved: {}", filePath);

        // Return the web-accessible path so it can be saved in DB and served via /media/** mapping
        return "media/" + THUMBNAILS_DIR + "/" + filename;
    }

    /**
     * Find videos within a radius from a given center point.
     * Uses configurable radius limits from application.properties.
     *
     * @param centerLat latitude of center
     * @param centerLon longitude of center
     * @param radius radius value in units specified by `units` (m, km, mi). If <= 0, uses default from config.
     * @param units "m" (meters), "km" (kilometers) or "mi" (miles). If null/empty, uses default from config.
     * @param page page index (0-based)
     * @param size page size
     */
    public PageResponse<Video> findVideosNearby(double centerLat, double centerLon, double radius, String units, int page, int size) {
        if (centerLat < -90 || centerLat > 90) throw new IllegalArgumentException("Latitude must be between -90 and 90");
        if (centerLon < -180 || centerLon > 180) throw new IllegalArgumentException("Longitude must be between -180 and 180");

        // Use configured defaults if not provided
        String effectiveUnits = (units == null || units.isEmpty()) ? defaultUnits : units;
        double effectiveRadius = (radius <= 0) ? defaultRadiusKm : radius;

        double radiusMeters;
        if ("km".equalsIgnoreCase(effectiveUnits)) {
            radiusMeters = effectiveRadius * 1000.0;
        } else if ("m".equalsIgnoreCase(effectiveUnits)) {
            radiusMeters = effectiveRadius;
        } else if ("mi".equalsIgnoreCase(effectiveUnits) || "mile".equalsIgnoreCase(effectiveUnits) || "miles".equalsIgnoreCase(effectiveUnits)) {
            radiusMeters = effectiveRadius * 1609.344;
        } else {
            throw new IllegalArgumentException("Unsupported units: " + effectiveUnits);
        }

        // Use configurable maximum radius from application.properties
        double maxRadiusMeters = maxRadiusKm * 1000.0;
        if (radiusMeters > maxRadiusMeters) {
            throw new IllegalArgumentException("Radius too large. Maximum allowed is " + maxRadiusKm + " km");
        }

        // compute bounding box for spatial index optimization
        // 1 degree latitude ~= 111.32 km
        double latDegreeMeters = 111320.0;
        double deltaLat = radiusMeters / latDegreeMeters;

        // longitude degrees depend on latitude
        double lonDegreeMeters = 111320.0 * Math.cos(Math.toRadians(centerLat));
        if (lonDegreeMeters <= 0) lonDegreeMeters = 1; // guard
        double deltaLon = radiusMeters / lonDegreeMeters;

        double minLat = centerLat - deltaLat;
        double maxLat = centerLat + deltaLat;
        double minLon = centerLon - deltaLon;
        double maxLon = centerLon + deltaLon;

        int validPage = Math.max(0, page);
        int validSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by(Sort.Direction.DESC, "created_at"));

        // Measure performance for [S2] requirement
        long startTime = System.currentTimeMillis();

        Page<Video> videoPage = videoRepository.findNearby(minLat, maxLat, minLon, maxLon, centerLat, centerLon, radiusMeters, pageable);
        videoPage.getContent().forEach(v -> v.setViewCount(videoViewRepository.countVideoViewByVideo_Id(v.getId())));

        // Record performance metric
        long responseTime = System.currentTimeMillis() - startTime;
        String locationStr = String.format("%.4f,%.4f", centerLat, centerLon);
        double radiusKmForMetric = radiusMeters / 1000.0;
        performanceMetricsService.recordMetric("NEARBY_SEARCH", responseTime,
                videoPage.getNumberOfElements(), "DISABLED", locationStr, radiusKmForMetric);

        return PageResponse.from(videoPage);
    }

    /**
     * Get configured default radius in kilometers.
     * @return default radius from application.properties
     */
    public double getDefaultRadiusKm() {
        return defaultRadiusKm;
    }

    /**
     * Get configured maximum radius in kilometers.
     * @return maximum radius from application.properties
     */
    public double getMaxRadiusKm() {
        return maxRadiusKm;
    }

    /**
     * Get configured default distance units.
     * @return default units from application.properties
     */
    public String getDefaultUnits() {
        return defaultUnits;
    }

    /**
     * Get user with location data from database.
     * @param userId User ID
     * @return User entity with location data or null if not found
     */
    public User getUserWithLocation(UUID userId) {
        if (userId == null) return null;
        return userService.getUserById(userId);
    }

    /**
     * Approximate user location based on IP address using ip-api.com free service.
     * @param request HTTP request to extract client IP from
     * @return double array [latitude, longitude] or null if approximation fails
     */
    public double[] approximateLocationByIp(HttpServletRequest request) {
        if (request == null) return null;

        try {
            String ip = extractClientIp(request);
            if (ip == null || ip.isBlank() || "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
                // Localhost - can't geolocate, return null
                return null;
            }

            URL url = new URL("http://ip-api.com/json/" + ip + "?fields=status,message,lat,lon");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            if (code != 200) return null;

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            String body = sb.toString();

            if (body.contains("\"status\":\"success\"")) {
                String latStr = extractJsonValue(body, "lat");
                String lonStr = extractJsonValue(body, "lon");
                if (latStr != null && lonStr != null) {
                    return new double[] { Double.parseDouble(latStr), Double.parseDouble(lonStr) };
                }
            }
        } catch (Exception e) {
            logger.warn("IP geolocation approximation failed: {}", e.getMessage());
        }
        return null;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractJsonValue(String json, String key) {
        String look = "\"" + key + "\":";
        int idx = json.indexOf(look);
        if (idx < 0) return null;
        int start = idx + look.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        boolean isString = json.charAt(start) == '"';
        if (isString) {
            start++;
            end = json.indexOf('"', start);
            if (end < 0) return null;
            return json.substring(start, end);
        } else {
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
            return json.substring(start, end);
        }
    }
}
