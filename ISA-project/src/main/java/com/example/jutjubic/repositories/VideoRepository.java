package com.example.jutjubic.repositories;

import com.example.jutjubic.models.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    Optional<Video> findVideoById(UUID id);
}