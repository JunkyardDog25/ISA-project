package com.example.jutjubic.repositories;

import com.example.jutjubic.models.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    Optional<Video> findVideoById(UUID id);

    /**
     * Pronalazi sve video objave za datog korisnika sa paginacijom.
     */
    Page<Video> findByCreatorId(UUID creatorId, Pageable pageable);
}