package com.example.jutjubic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Time;
import java.time.LocalDateTime;

/**
 * DTO for creating a new video by authenticated user
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CreateVideoDto {
    private String title;
    private String description;
    private String videoPath;
    private String thumbnailPath;
    private String thumbnailCompressedPath;
    private Long fileSize;
    private Time duration;
    private Boolean transcoded;
    private LocalDateTime scheduledAt;
    private String country;
}
