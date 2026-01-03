package com.example.jutjubic.dto;

import com.example.jutjubic.models.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Time;
import java.time.LocalDateTime;

/**
 * DTO for {@link com.example.jutjubic.models.Video}
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class VideoDto {
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
    private Long viewCount;
    private User user;
}