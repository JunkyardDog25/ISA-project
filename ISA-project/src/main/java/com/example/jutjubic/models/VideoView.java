package com.example.jutjubic.models;

import com.example.jutjubic.dto.VideoViewDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video_views")
@NoArgsConstructor
@Getter @Setter
public class VideoView {
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private Video video;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at")
    private Instant createdAt;

    public VideoView(Video video, User user) {
        this.video = video;
        this.user = user;
        this.createdAt = Instant.now();
    }

    public VideoView(VideoViewDto videoViewDto) {
        this.video = videoViewDto.getVideo();
        this.user = videoViewDto.getUser();
        this.createdAt = Instant.now();
    }
}