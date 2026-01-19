package com.example.jutjubic.dto;

import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

/**
 * DTO for {@link com.example.jutjubic.models.VideoView}
 */
@AllArgsConstructor
@Getter
public class VideoViewDto {
    private final Video video;
    private final User user;
}