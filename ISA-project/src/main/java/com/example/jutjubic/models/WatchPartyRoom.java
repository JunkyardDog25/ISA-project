package com.example.jutjubic.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entitet koji predstavlja Watch Party sobu.
 * Korisnici mogu kreirati sobu i deliti link sa drugima koji se mogu pridružiti.
 */
@Entity
@Table(name = "watch_party_rooms")
@Getter @Setter @NoArgsConstructor
public class WatchPartyRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Jedinstveni kod za pridruživanje sobi (kratki kod umesto UUID-a za lakše deljenje).
     */
    @Column(name = "room_code", nullable = false, unique = true, length = 8)
    private String roomCode;

    /**
     * Vlasnik/kreator sobe.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Video koji se trenutno gleda u sobi (može biti null ako još nije izabran).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_video_id")
    private Video currentVideo;

    /**
     * Da li je soba aktivna (otvorena za nove članove).
     */
    @Column(name = "is_active")
    private boolean active = true;

    /**
     * Da li je soba javno vidljiva na početnoj stranici.
     */
    @Column(name = "is_public")
    private boolean isPublic = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Broj trenutno aktivnih članova u sobi (ne čuva se u bazi, prati se u memoriji).
     */
    @Transient
    private int memberCount = 0;

    public WatchPartyRoom(String name, String description, User owner, boolean isPublic) {
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.isPublic = isPublic;
        this.active = true;
        this.roomCode = generateRoomCode();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Generiše nasumični 8-karakterni kod za sobu.
     */
    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int index = (int) (Math.random() * chars.length());
            code.append(chars.charAt(index));
        }
        return code.toString();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}


