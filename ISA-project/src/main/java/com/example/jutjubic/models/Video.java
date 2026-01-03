package com.example.jutjubic.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "videos")
@Getter @Setter @NoArgsConstructor
public class Video {
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column
    private String title;

    @Column
    private String description;

    @Column(name = "video_path")
    private String videoPath;

    @Column(name = "thumbnail_path")
    private String thumbnailPath;

    @Column(name = "thumbnail_compressed_path")
    private String thumbnailCompressedPath;

    @Column
    private long fileSize;

    @Column
    private Time duration;

    @Column(name = "is_transcoded")
    private boolean transcoded;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column
    private String country;

    @Column(name = "view_count")
    private long viewCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User creator;

    public Video(String title, String description, String videoPath, String thumbnailPath, String thumbnailCompressedPath, long fileSize, Time duration, boolean transcoded, LocalDateTime scheduledAt, String country, long viewCount, User user) {
        this.title = title;
        this.description = description;
        this.videoPath = videoPath;
        this.thumbnailPath = thumbnailPath;
        this.thumbnailCompressedPath = thumbnailCompressedPath;
        this.fileSize = fileSize;
        this.duration = duration;
        this.transcoded = transcoded;
        this.scheduledAt = scheduledAt;
        this.country = country;
        this.viewCount = viewCount;
        this.creator = user;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}