package com.example.jutjubic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
public class CommentDto {
    private UUID id;
    private UUID userId;
    private String username;
    private UUID videoId;
    private String content;
    private LocalDateTime createdAt;
}
