package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.CommentDto;
import com.example.jutjubic.dto.CommentLimitResponse;
import com.example.jutjubic.dto.CommentPageResponse;
import com.example.jutjubic.dto.ErrorResponse;
import com.example.jutjubic.exceptions.CommentLimitExceededException;
import com.example.jutjubic.models.Comment;
import com.example.jutjubic.services.CommentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * Dobija paginiranu listu komentara za video.
     * Komentari su sortirani od najnovijeg do najstarijeg.
     * Rezultat je keširan.
     *
     * @param videoId ID videa
     * @param page Broj stranice (0-based, default: 0)
     * @param size Veličina stranice (default: 20, max: 100)
     * @return Paginirana lista komentara sa metapodacima
     */
    @GetMapping("/video/{videoId}")
    public ResponseEntity<CommentPageResponse> getCommentsByVideoIdPaginated(
            @PathVariable UUID videoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        CommentPageResponse comments = commentService.getCommentsByVideoIdPaginated(videoId, page, size);
        return ResponseEntity.ok(comments);
    }

    /**
     * Dobija sve komentare za video bez paginacije (legacy endpoint).
     *
     * @deprecated Koristi GET /video/{videoId} sa parametrima page i size
     */
    @Deprecated
    @GetMapping("/video/{videoId}/all")
    public ResponseEntity<List<CommentDto>> getAllCommentsByVideoId(@PathVariable UUID videoId) {
        List<CommentDto> comments = commentService.getAllCommentsByVideoId(videoId);
        return ResponseEntity.ok(comments);
    }

    /**
     * Kreira novi komentar na videu.
     * Samo registrovani korisnici mogu komentarisati.
     * Ograničenje: 60 komentara po satu po korisniku.
     *
     * @param videoId ID videa
     * @param commentDto Podaci komentara (userId i content)
     * @return Kreirani komentar
     */
    @PostMapping("/video/{videoId}")
    public ResponseEntity<?> createComment(
            @PathVariable UUID videoId,
            @RequestBody CommentDto commentDto
    ) {
        try {
            Comment createdComment = commentService.createComment(videoId, commentDto);
            return ResponseEntity.ok(createdComment);
        } catch (CommentLimitExceededException e) {
            ErrorResponse error = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "COMMENT_LIMIT_EXCEEDED",
                e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
        }
    }

    /**
     * Dobija informacije o ograničenju komentara za korisnika.
     *
     * @param userId ID korisnika
     * @return Informacije o broju preostalih komentara
     */
    @GetMapping("/limit/{userId}")
    public ResponseEntity<CommentLimitResponse> getCommentLimitStatus(@PathVariable UUID userId) {
        CommentLimitResponse limitStatus = commentService.getCommentLimitStatus(userId);
        return ResponseEntity.ok(limitStatus);
    }

    /**
     * Dobija ukupan broj komentara za video.
     *
     * @param videoId ID videa
     * @return Broj komentara
     */
    @GetMapping("/video/{videoId}/count")
    public ResponseEntity<Long> getCommentCount(@PathVariable UUID videoId) {
        long count = commentService.getCommentCount(videoId);
        return ResponseEntity.ok(count);
    }
}


