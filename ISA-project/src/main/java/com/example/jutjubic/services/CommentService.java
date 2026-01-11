package com.example.jutjubic.services;

import com.example.jutjubic.config.CacheConfig;
import com.example.jutjubic.dto.CommentDto;
import com.example.jutjubic.dto.CommentLimitResponse;
import com.example.jutjubic.dto.CommentPageResponse;
import com.example.jutjubic.exceptions.CommentLimitExceededException;
import com.example.jutjubic.models.Comment;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.repositories.CommentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CommentService {

    private static final Logger logger = LoggerFactory.getLogger(CommentService.class);

/**
     * Maksimalan broj komentara po korisniku u sat vremena.
     */
    public static final int MAX_COMMENTS_PER_HOUR = 60;

    /**
     * Maksimalna veličina stranice za paginaciju.
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final CommentRepository commentRepository;
    private final UserService userService;
    private final VideoService videoService;

    public CommentService(CommentRepository commentRepository, UserService userService, VideoService videoService) {
        this.commentRepository = commentRepository;
        this.userService = userService;
        this.videoService = videoService;
    }

    /**
     * Dobija paginiranu listu komentara za video.
     * Komentari su sortirani od najnovijeg do najstarijeg.
     * Rezultat se kešira po videoId, page i size.
     *
     * @param videoId ID videa
     * @param page Broj stranice (0-based)
     * @param size Veličina stranice
     * @return Paginirana lista komentara
     */
    @Cacheable(
        value = CacheConfig.COMMENTS_CACHE,
        key = "#videoId + '-' + #page + '-' + #size",
        unless = "#result == null"
    )
    public CommentPageResponse getCommentsByVideoIdPaginated(UUID videoId, int page, int size) {
        logger.debug("Fetching comments for video {} - page: {}, size: {} (not from cache)", videoId, page, size);

        int validPage = Math.max(0, page);
        int validSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        // Sortiranje po createdAt DESC (najnoviji prvi)
        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Comment> commentPage = commentRepository.findByVideoId(videoId, pageable);

        // Mapiranje Comment -> CommentDto
        List<CommentDto> commentDtos = commentPage.getContent().stream()
                .map(this::mapToDto)
                .toList();

        return CommentPageResponse.from(commentDtos, commentPage);
    }

    /**
     * Dobija sve komentare za video bez paginacije (legacy metoda).
     * Komentari su sortirani od najnovijeg do najstarijeg.
     *
     * @deprecated Koristi {@link #getCommentsByVideoIdPaginated} za paginaciju
     */
    @Deprecated
    public List<CommentDto> getAllCommentsByVideoId(UUID videoId) {
        List<Comment> comments = commentRepository.findAllByVideo_Id(videoId);
        return comments.stream()
                .sorted((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt())) // DESC sorting
                .map(this::mapToDto)
                .toList();
    }

    /**
     * Kreira novi komentar na videu.
     * Proverava da li je korisnik prekoračio ograničenje od 60 komentara po satu.
     *
     * @param videoId ID videa
     * @param commentDto Podaci komentara
     * @return Kreirani komentar
     * @throws CommentLimitExceededException ako je korisnik prekoračio limit
     */
    @Transactional
    @CacheEvict(value = CacheConfig.COMMENTS_CACHE, allEntries = true)
    public Comment createComment(UUID videoId, CommentDto commentDto) {
        UUID userId = commentDto.getUserId();

        // Provera ograničenja broja komentara
        checkCommentLimit(userId);

        User user = userService.getUserById(userId);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            throw new RuntimeException("Video not found");
        }

        Comment comment = new Comment(user, video, commentDto.getContent());
        Comment savedComment = commentRepository.save(comment);

        logger.info("User {} created comment on video {}. Comment ID: {}", userId, videoId, savedComment.getId());

        return savedComment;
    }

    /**
     * Proverava da li je korisnik prekoračio ograničenje broja komentara.
     *
     * @param userId ID korisnika
     * @throws CommentLimitExceededException ako je limit prekoračen
     */
    private void checkCommentLimit(UUID userId) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long commentCount = commentRepository.countByUserIdAndCreatedAtAfter(userId, oneHourAgo);

        if (commentCount >= MAX_COMMENTS_PER_HOUR) {
            logger.warn("User {} exceeded comment limit. Current count: {}", userId, commentCount);
            throw new CommentLimitExceededException(MAX_COMMENTS_PER_HOUR, (int) commentCount, 60);
        }

        logger.debug("User {} has {} comments in the last hour. Limit: {}", userId, commentCount, MAX_COMMENTS_PER_HOUR);
    }

    /**
     * Dobija informacije o ograničenju komentara za korisnika.
     *
     * @param userId ID korisnika
     * @return Informacije o ograničenju
     */
    public CommentLimitResponse getCommentLimitStatus(UUID userId) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long commentCount = commentRepository.countByUserIdAndCreatedAtAfter(userId, oneHourAgo);
        return CommentLimitResponse.of(MAX_COMMENTS_PER_HOUR, (int) commentCount);
    }

    /**
     * Broji ukupan broj komentara za video.
     */
    @Cacheable(value = CacheConfig.COMMENT_COUNT_CACHE, key = "#videoId")
    public long getCommentCount(UUID videoId) {
        return commentRepository.countByVideoId(videoId);
    }

    /**
     * Mapira Comment entitet na CommentDto.
     */
    private CommentDto mapToDto(Comment comment) {
        return new CommentDto(
                comment.getId(),
                comment.getUser().getId(),
                comment.getUser().getUsername(),
                comment.getVideo().getId(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}



