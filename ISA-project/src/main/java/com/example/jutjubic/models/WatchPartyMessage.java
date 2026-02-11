package com.example.jutjubic.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entitet koji predstavlja chat poruku u Watch Party sobi.
 */
@Entity
@Table(name = "watch_party_messages")
@Getter @Setter @NoArgsConstructor
public class WatchPartyMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    /**
     * Soba u kojoj je poruka poslata.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private WatchPartyRoom room;

    /**
     * Korisnik koji je poslao poruku (null za sistemske poruke).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id")
    private User sender;

    /**
     * Username pošiljaoca (čuva se za slučaj da korisnik obriše nalog).
     */
    @Column(name = "sender_username", nullable = false)
    private String senderUsername;

    /**
     * Sadržaj poruke.
     */
    @Column(nullable = false, length = 1000)
    private String content;

    /**
     * Tip poruke (CHAT, JOIN, LEAVE, SYSTEM).
     */
    @Column(name = "message_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private MessageType messageType = MessageType.CHAT;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        SYSTEM
    }

    public WatchPartyMessage(WatchPartyRoom room, User sender, String senderUsername,
                              String content, MessageType messageType) {
        this.room = room;
        this.sender = sender;
        this.senderUsername = senderUsername;
        this.content = content;
        this.messageType = messageType;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

