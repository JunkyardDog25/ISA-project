package com.example.jutjubic.repositories;

import com.example.jutjubic.models.VideoView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VideoViewRepository extends JpaRepository<VideoView, UUID> {

    int countVideoViewByVideo_Id(UUID videoId);
}