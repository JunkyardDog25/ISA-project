package com.example.jutjubic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Time;

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
    private String tags;
    private String videoPath;
    private String thumbnailPath;
    private Long fileSize;
    private Double latitude;
    private Double longitude;
    private Time duration;
}
