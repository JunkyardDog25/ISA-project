package com.example.jutjubic.repositories;

import com.example.jutjubic.models.Like;
import com.example.jutjubic.models.LikeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LikeRepository extends JpaRepository<Like, LikeId> {
    boolean existsById(LikeId id);
    long countByVideoId(UUID videoId);
}