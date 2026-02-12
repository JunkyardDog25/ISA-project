package com.example.jutjubic.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * DTO za WebSocket poruke u Watch Party sobi.
 * Koristi se za sinhronizaciju videa između članova sobe.
 */
@Getter @Setter
public class WatchPartyMessageDto {

    private String roomCode;
    private UUID videoId;
    private String videoTitle;
    private String videoThumbnail;
    private String senderUsername;
    private UUID senderId;
    private long timestamp;
    private MessageType type;
    private String content;

    public enum MessageType {
        /**
         * Vlasnik sobe je pokrenuo video - svi članovi treba da otvore stranicu tog videa.
         */
        PLAY_VIDEO,

        /**
         * Korisnik se pridružio sobi.
         */
        JOIN,

        /**
         * Korisnik je napustio sobu.
         */
        LEAVE,

        /**
         * Chat poruka u sobi.
         */
        CHAT,

        /**
         * Ažuriranje broja članova u sobi.
         */
        MEMBER_COUNT_UPDATE,

        /**
         * Soba je zatvorena od strane vlasnika.
         */
        ROOM_CLOSED
    }

    public WatchPartyMessageDto() {
    }

    public WatchPartyMessageDto(String roomCode, UUID senderId, String senderUsername, MessageType type) {
        this.roomCode = roomCode;
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public WatchPartyMessageDto(String roomCode, UUID videoId, String videoTitle,
                                 UUID senderId, String senderUsername, MessageType type) {
        this.roomCode = roomCode;
        this.videoId = videoId;
        this.videoTitle = videoTitle;
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
}

