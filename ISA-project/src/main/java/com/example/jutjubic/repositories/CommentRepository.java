package com.example.jutjubic.repositories;

import com.example.jutjubic.models.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Pronalazi sve komentare za video (legacy metoda - bez paginacije).
     */
    List<Comment> findAllByVideo_Id(UUID videoId);

    /**
     * Pronalazi komentare za video sa paginacijom.
     * Sortiranje se vrši preko Pageable parametra.
     */
    Page<Comment> findByVideoId(UUID videoId, Pageable pageable);

    /**
     * Broji ukupan broj komentara za određeni video.
     */
    long countByVideoId(UUID videoId);

    /**
     * Broji broj komentara koje je korisnik postavio u određenom vremenskom periodu.
     * Koristi se za ograničenje od 60 komentara po satu.
     */
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.user.id = :userId AND c.createdAt >= :since")
    long countByUserIdAndCreatedAtAfter(@Param("userId") UUID userId, @Param("since") LocalDateTime since);
}

