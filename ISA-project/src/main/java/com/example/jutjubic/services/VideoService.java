package com.example.jutjubic.services;

import com.example.jutjubic.dto.CreateVideoDto;
import com.example.jutjubic.dto.VideoDto;
import com.example.jutjubic.dto.ViewResponseDto;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.repositories.VideoRepository;
import com.example.jutjubic.utils.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private static final String STATIC_RESOURCES_PATH = "ISA-project/src/main/resources/static";
    private static final String VIDEOS_DIR = "videos";
    private static final String THUMBNAILS_DIR = "thumbnails";

    private final VideoRepository videoRepository;
    private final UserService userService;

    public VideoService(VideoRepository videoRepository, UserService userService) {
        this.videoRepository = videoRepository;
        this.userService = userService;
        
        // Ensure directories exist
        try {
            Path videosPath = Paths.get(STATIC_RESOURCES_PATH, VIDEOS_DIR);
            Path thumbnailsPath = Paths.get(STATIC_RESOURCES_PATH, THUMBNAILS_DIR);
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
                videoDto.getCountry(),
                null, // tags - not in VideoDto, can be null
                videoDto.getViewCount() != null ? videoDto.getViewCount() : 0,
                videoDto.getUser()
        );

        return videoRepository.save(video);
    }

    private static final long MAX_VIDEO_SIZE = 200L * 1024 * 1024; // 200MB in bytes

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
                new Time(0), // duration - will be set later if needed
                false, // transcoded - default false
                null, // scheduledAt - not needed for immediate posting
                createVideoDto.getCountry(),
                createVideoDto.getTags(),
                0L, // viewCount - starts at 0
                managedUser
        );
        
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

        return PageResponse.from(videoPage);
    }

    public Video getVideoById(UUID id) {
        Optional<Video> optionalVideo = videoRepository.findVideoById(id);
        return optionalVideo.orElse(null);
    }


    @Transactional
    public ViewResponseDto incrementViews(UUID videoId) {
        int rowsUpdated = videoRepository.incrementViewCount(videoId);
        
        if (rowsUpdated == 0) {
            throw new RuntimeException("Video not found");
        }
        
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        return new ViewResponseDto(true, video.getViewCount());
    }
    
    /**
     * Saves uploaded video file to the static resources directory.
     * 
     * @param videoFile MultipartFile containing the video
     * @return Relative path to the saved video file (e.g., "videos/video_123.mp4")
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
        
        Path videosPath = Paths.get(STATIC_RESOURCES_PATH, VIDEOS_DIR);
        Path filePath = videosPath.resolve(filename);
        
        Files.copy(videoFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Video file saved: {}", filePath);
        
        return VIDEOS_DIR + "/" + filename;
    }
    
    /**
     * Saves uploaded thumbnail file to the static resources directory.
     * 
     * @param thumbnailFile MultipartFile containing the thumbnail image
     * @return Relative path to the saved thumbnail file (e.g., "thumbnails/thumb_123.jpg")
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
        
        Path thumbnailsPath = Paths.get(STATIC_RESOURCES_PATH, THUMBNAILS_DIR);
        Path filePath = thumbnailsPath.resolve(filename);
        
        Files.copy(thumbnailFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Thumbnail file saved: {}", filePath);
        
        return THUMBNAILS_DIR + "/" + filename;
    }
}
