package com.example.jutjubic.repositories;

import com.example.jutjubic.models.VideoView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface VideoViewRepository extends JpaRepository<VideoView, UUID> {

    int countVideoViewByVideo_Id(UUID videoId);

    @Query("SELECT vv FROM VideoView vv JOIN FETCH vv.video")
    Page<VideoView> findAllWithVideo(Pageable pageable);
}