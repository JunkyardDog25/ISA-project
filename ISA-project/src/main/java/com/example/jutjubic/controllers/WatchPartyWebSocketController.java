package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.WatchPartyMessageDto;
import com.example.jutjubic.models.WatchPartyMessage;
import com.example.jutjubic.services.WatchPartyService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebSocket kontroler za Watch Party sinhronizaciju.
 * Omogućava real-time komunikaciju između članova Watch Party sobe.
 */
@RestController
public class WatchPartyWebSocketController {

    private final WatchPartyService watchPartyService;
    private final SimpMessagingTemplate messagingTemplate;

    public WatchPartyWebSocketController(WatchPartyService watchPartyService,
                                          SimpMessagingTemplate messagingTemplate) {
        this.watchPartyService = watchPartyService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Vlasnik sobe pokreće video - šalje PLAY_VIDEO poruku svim članovima.
     * Svi članovi primaju ovu poruku i treba da otvore stranicu tog videa.
     */
    @MessageMapping("/watch-party/{roomCode}/play")
    @SendTo("/topic/watch-party/{roomCode}")
    public WatchPartyMessageDto playVideo(
            @DestinationVariable String roomCode,
            @Payload WatchPartyMessageDto message) {

        message.setRoomCode(roomCode.toUpperCase());
        message.setType(WatchPartyMessageDto.MessageType.PLAY_VIDEO);
        message.setTimestamp(System.currentTimeMillis());

        return message;
    }

    /**
     * Korisnik se pridružuje Watch Party sobi.
     */
    @MessageMapping("/watch-party/{roomCode}/join")
    @SendTo("/topic/watch-party/{roomCode}")
    public WatchPartyMessageDto userJoined(
            @DestinationVariable String roomCode,
            @Payload WatchPartyMessageDto message) {

        // Ažuriraj broj članova koristeći sender ID
        String oderId = message.getSenderId() != null ? message.getSenderId().toString() : "guest-" + System.currentTimeMillis();
        int memberCount = watchPartyService.memberJoined(roomCode.toUpperCase(), oderId);

        message.setRoomCode(roomCode.toUpperCase());
        message.setType(WatchPartyMessageDto.MessageType.JOIN);
        message.setTimestamp(System.currentTimeMillis());
        String content = message.getSenderUsername() + " joined the party";
        message.setContent(content);

        // Sačuvaj poruku u bazu
        try {
            watchPartyService.saveMessage(
                roomCode,
                message.getSenderId(),
                message.getSenderUsername(),
                content,
                WatchPartyMessage.MessageType.JOIN
            );
        } catch (Exception e) {
            // Log error but don't fail the message
            System.err.println("Failed to save join message: " + e.getMessage());
        }

        // Pošalji i ažuriranje broja članova
        sendMemberCountUpdate(roomCode, memberCount);

        return message;
    }

    /**
     * Korisnik napušta Watch Party sobu.
     */
    @MessageMapping("/watch-party/{roomCode}/leave")
    @SendTo("/topic/watch-party/{roomCode}")
    public WatchPartyMessageDto userLeft(
            @DestinationVariable String roomCode,
            @Payload WatchPartyMessageDto message) {

        // Ažuriraj broj članova koristeći sender ID
        String oderId = message.getSenderId() != null ? message.getSenderId().toString() : "unknown";
        int memberCount = watchPartyService.memberLeft(roomCode.toUpperCase(), oderId);

        message.setRoomCode(roomCode.toUpperCase());
        message.setType(WatchPartyMessageDto.MessageType.LEAVE);
        message.setTimestamp(System.currentTimeMillis());
        String content = message.getSenderUsername() + " left the party";
        message.setContent(content);

        // Sačuvaj poruku u bazu
        try {
            watchPartyService.saveMessage(
                roomCode,
                message.getSenderId(),
                message.getSenderUsername(),
                content,
                WatchPartyMessage.MessageType.LEAVE
            );
        } catch (Exception e) {
            System.err.println("Failed to save leave message: " + e.getMessage());
        }

        // Pošalji i ažuriranje broja članova
        sendMemberCountUpdate(roomCode, memberCount);

        return message;
    }

    /**
     * Chat poruka u Watch Party sobi.
     */
    @MessageMapping("/watch-party/{roomCode}/chat")
    @SendTo("/topic/watch-party/{roomCode}")
    public WatchPartyMessageDto chatMessage(
            @DestinationVariable String roomCode,
            @Payload WatchPartyMessageDto message) {

        message.setRoomCode(roomCode.toUpperCase());
        message.setType(WatchPartyMessageDto.MessageType.CHAT);
        message.setTimestamp(System.currentTimeMillis());

        // Sačuvaj poruku u bazu
        try {
            watchPartyService.saveMessage(
                roomCode,
                message.getSenderId(),
                message.getSenderUsername(),
                message.getContent(),
                WatchPartyMessage.MessageType.CHAT
            );
        } catch (Exception e) {
            System.err.println("Failed to save chat message: " + e.getMessage());
        }

        return message;
    }

    /**
     * Vlasnik zatvara sobu.
     */
    @MessageMapping("/watch-party/{roomCode}/close")
    @SendTo("/topic/watch-party/{roomCode}")
    public WatchPartyMessageDto closeRoom(
            @DestinationVariable String roomCode,
            @Payload WatchPartyMessageDto message) {

        message.setRoomCode(roomCode.toUpperCase());
        message.setType(WatchPartyMessageDto.MessageType.ROOM_CLOSED);
        message.setTimestamp(System.currentTimeMillis());
        message.setContent("Watch Party soba je zatvorena od strane vlasnika");

        return message;
    }

    /**
     * Šalje ažuriranje broja članova svim korisnicima u sobi.
     */
    private void sendMemberCountUpdate(String roomCode, int memberCount) {
        WatchPartyMessageDto countMessage = new WatchPartyMessageDto();
        countMessage.setRoomCode(roomCode.toUpperCase());
        countMessage.setType(WatchPartyMessageDto.MessageType.MEMBER_COUNT_UPDATE);
        countMessage.setTimestamp(System.currentTimeMillis());
        countMessage.setContent(String.valueOf(memberCount));

        messagingTemplate.convertAndSend(
                "/topic/watch-party/" + roomCode.toUpperCase(),
                countMessage
        );
    }
}

