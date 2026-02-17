package com.example.jutjubicmqconsumer.messaging;


public record UploadEventJson(
        String videoId,
        String title,
        Long sizeMB,
        String authorUsername,
        String createdAt
) {}