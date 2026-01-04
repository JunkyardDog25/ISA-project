package com.example.jutjubic.services;

import com.example.jutjubic.dto.LikeResponseDto;
import com.example.jutjubic.models.Like;
import com.example.jutjubic.models.LikeId;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.repositories.LikeRepository;
import com.example.jutjubic.repositories.UserRepository;
import com.example.jutjubic.repositories.VideoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    public LikeService(LikeRepository likeRepository, UserRepository userRepository, VideoRepository videoRepository) {
        this.likeRepository = likeRepository;
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
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
}

