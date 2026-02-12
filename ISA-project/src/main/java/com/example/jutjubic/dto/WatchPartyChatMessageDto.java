package com.example.jutjubic.dto;

import com.example.jutjubic.models.WatchPartyMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * DTO za Watch Party chat poruku.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class WatchPartyChatMessageDto {

    private UUID id;
    private UUID senderId;
    private String senderUsername;
    private String content;
    private String type;
    private long timestamp;

    public static WatchPartyChatMessageDto fromEntity(WatchPartyMessage message) {
        WatchPartyChatMessageDto dto = new WatchPartyChatMessageDto();
        dto.setId(message.getId());
        dto.setSenderId(message.getSender() != null ? message.getSender().getId() : null);
        dto.setSenderUsername(message.getSenderUsername());
        dto.setContent(message.getContent());
        dto.setType(message.getMessageType().name());
        dto.setTimestamp(message.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        return dto;
    }
}


