package com.example.jutjubic.services;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Time;

@Service
public class VideoMetadataService {

    public Time getVideoDuration(String videoPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoPath
        );

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        String durationStr = reader.readLine();
        process.waitFor();

        double totalSeconds = Double.parseDouble(durationStr);
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        int seconds = (int) (totalSeconds % 60);
        return Time.valueOf(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }
}
