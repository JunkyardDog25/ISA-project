package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.ChatMessageDto;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebSocket controller za grupni chat u toku video streaming-a.
 * Svaki video ima svoju chat sobu identifikovanu po videoId.
 * Poruke se ne čuvaju - samo se prosleđuju aktivnim korisnicima.
 */
@RestController
public class ChatController {

    /**
     * Prima chat poruku od korisnika i prosleđuje je svim korisnicima u istoj chat sobi.
     * Chat soba je identifikovana po videoId.
     *
     * @param videoId ID videa koji služi kao identifikator chat sobe
     * @param message Chat poruka
     * @return Chat poruka sa timestamp-om
     */
    @MessageMapping("/chat/{videoId}")
    @SendTo("/topic/chat/{videoId}")
    public ChatMessageDto sendMessage(
            @DestinationVariable String videoId,
            @Payload ChatMessageDto message) {

        // Postavi timestamp ako nije postavljen
        if (message.getTimestamp() == 0) {
            message.setTimestamp(System.currentTimeMillis());
        }

        // Postavi videoId iz URL-a
        message.setVideoId(videoId);

        // Ako tip nije postavljen, postavi kao CHAT
        if (message.getType() == null) {
            message.setType(ChatMessageDto.MessageType.CHAT);
        }

        return message;
    }

    /**
     * Obaveštava sve korisnike u chat sobi kada se novi korisnik priključi.
     *
     * @param videoId ID videa / chat sobe
     * @param message Poruka sa informacijama o korisniku koji se priključuje
     * @return JOIN poruka
     */
    @MessageMapping("/chat/{videoId}/join")
    @SendTo("/topic/chat/{videoId}")
    public ChatMessageDto userJoined(
            @DestinationVariable String videoId,
            @Payload ChatMessageDto message) {

        message.setVideoId(videoId);
        message.setType(ChatMessageDto.MessageType.JOIN);
        message.setTimestamp(System.currentTimeMillis());
        message.setContent(message.getSenderUsername() + " se priključio/la chatu");

        return message;
    }

    /**
     * Obaveštava sve korisnike u chat sobi kada korisnik napusti chat.
     *
     * @param videoId ID videa / chat sobe
     * @param message Poruka sa informacijama o korisniku koji napušta
     * @return LEAVE poruka
     */
    @MessageMapping("/chat/{videoId}/leave")
    @SendTo("/topic/chat/{videoId}")
    public ChatMessageDto userLeft(
            @DestinationVariable String videoId,
            @Payload ChatMessageDto message) {

        message.setVideoId(videoId);
        message.setType(ChatMessageDto.MessageType.LEAVE);
        message.setTimestamp(System.currentTimeMillis());
        message.setContent(message.getSenderUsername() + " je napustio/la chat");

        return message;
    }
}

