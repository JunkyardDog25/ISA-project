package com.example.jutjubic.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO za chat poruke u video streaming chat-u.
 */
@Setter
@Getter
public class ChatMessageDto {

    private String videoId;
    private String content;
    private String senderUsername;
    private String senderId;
    private long timestamp;
    private MessageType type;

    public enum MessageType {
        CHAT,      // Normalna chat poruka
        JOIN,      // Korisnik se priključio
        LEAVE      // Korisnik je napustio
    }

    public ChatMessageDto() {
    }

    public ChatMessageDto(String videoId, String content, String senderUsername, String senderId, MessageType type) {
        this.videoId = videoId;
        this.content = content;
        this.senderUsername = senderUsername;
        this.senderId = senderId;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

}
