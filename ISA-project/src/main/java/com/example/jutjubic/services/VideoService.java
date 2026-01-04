package com.example.jutjubic.services;

import com.example.jutjubic.dto.LikeResponseDto;
import com.example.jutjubic.dto.VideoDto;
import com.example.jutjubic.dto.ViewResponseDto;
import com.example.jutjubic.models.Like;
import com.example.jutjubic.models.LikeId;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.repositories.LikeRepository;
import com.example.jutjubic.repositories.UserRepository;
import com.example.jutjubic.repositories.VideoRepository;
import com.example.jutjubic.utils.PageResponse;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.sql.Time;
import java.util.Optional;
import java.util.UUID;

@Service
public class VideoService {

    private static final int MAX_PAGE_SIZE = 100;

    private final VideoRepository videoRepository;
    private final LikeRepository likeRepository;
    private final UserRepository userRepository;

    public VideoService(VideoRepository videoRepository, LikeRepository likeRepository, UserRepository userRepository) {
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.userRepository = userRepository;
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
                videoDto.getViewCount() != null ? videoDto.getViewCount() : 0,
                videoDto.getUser()
        );

        return videoRepository.save(video);
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
    public LikeResponseDto toggleLike(UUID videoId, UUID userId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LikeId likeId = new LikeId(userId, videoId);

        boolean isLiked;
        if (likeRepository.existsById(likeId)) {
            likeRepository.deleteById(likeId);
            isLiked = false;
        } else {
            Like like = new Like(likeId, user, video);
            likeRepository.save(like);
            isLiked = true;
        }

        long likeCount = likeRepository.countByVideoId(videoId);
        return new LikeResponseDto(isLiked, likeCount);
    }

    public LikeResponseDto getLikeStatus(UUID videoId, UUID userId) {
        LikeId likeId = new LikeId(userId, videoId);
        boolean isLiked = likeRepository.existsById(likeId);
        long likeCount = likeRepository.countByVideoId(videoId);
        return new LikeResponseDto(isLiked, likeCount);
    }

    public long getLikeCount(UUID videoId) {
        return likeRepository.countByVideoId(videoId);
    }

    public ViewResponseDto incrementViews(UUID videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        video.setViewCount(video.getViewCount() + 1);
        videoRepository.save(video);

        return new ViewResponseDto(true, video.getViewCount());
    }
}
