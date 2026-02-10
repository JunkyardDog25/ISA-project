package com.example.jutjubic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;


@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TranscodingJobMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID of the video being transcoded
     */
    private UUID videoId;

    /**
     * Source path of the original video file
     */
    private String sourcePath;

    /**
     * Output path for the transcoded video
     */
    private String outputPath;

    /**
     * Video codec to use (e.g., libx264)
     */
    private String videoCodec;

    /**
     * Audio codec to use (e.g., aac)
     */
    private String audioCodec;

    /**
     * Target resolution (e.g., 1280x720)
     */
    private String resolution;

    /**
     * Video bitrate in bits per second
     */
    private Long videoBitrate;

    /**
     * Audio bitrate in bits per second
     */
    private Long audioBitrate;

    /**
     * Output format (e.g., mp4)
     */
    private String format;

    @Override
    public String toString() {
        return "TranscodingJobMessage{" +
                "videoId=" + videoId +
                ", sourcePath='" + sourcePath + '\'' +
                ", outputPath='" + outputPath + '\'' +
                ", videoCodec='" + videoCodec + '\'' +
                ", resolution='" + resolution + '\'' +
                ", format='" + format + '\'' +
                '}';
    }
}

