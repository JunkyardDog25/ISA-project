package com.example.jutjubic.dto;

import com.example.jutjubic.models.WatchPartyRoom;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO za prikaz Watch Party sobe.
 */
@Getter @Setter
public class WatchPartyRoomDto {

    private UUID id;
    private String name;
    private String description;
    private String roomCode;
    private UUID ownerId;
    private String ownerUsername;
    private UUID currentVideoId;
    private String currentVideoTitle;
    private String currentVideoThumbnail;
    private String currentVideoPath;
    private boolean active;
    private boolean isPublic;
    private int memberCount;
    private LocalDateTime createdAt;

    /**
     * Vrijeme u sekundama od početka videa (za sinhronizaciju novih članova).
     */
    private long videoElapsedSeconds;

    public WatchPartyRoomDto() {
    }

    /**
     * Kreira DTO iz entiteta.
     */
    public static WatchPartyRoomDto fromEntity(WatchPartyRoom room) {
        WatchPartyRoomDto dto = new WatchPartyRoomDto();
        dto.setId(room.getId());
        dto.setName(room.getName());
        dto.setDescription(room.getDescription());
        dto.setRoomCode(room.getRoomCode());
        dto.setOwnerId(room.getOwner().getId());
        dto.setOwnerUsername(room.getOwner().getUsername());
        dto.setActive(room.isActive());
        dto.setPublic(room.isPublic());
        dto.setMemberCount(room.getMemberCount());
        dto.setCreatedAt(room.getCreatedAt());

        if (room.getCurrentVideo() != null) {
            dto.setCurrentVideoId(room.getCurrentVideo().getId());
            dto.setCurrentVideoTitle(room.getCurrentVideo().getTitle());
            dto.setCurrentVideoThumbnail(room.getCurrentVideo().getThumbnailPath());
            dto.setCurrentVideoPath(room.getCurrentVideo().getVideoPath());

            // Izračunaj koliko je vremena prošlo od početka videa
            if (room.getVideoStartedAt() != null) {
                long elapsed = Duration.between(room.getVideoStartedAt(), LocalDateTime.now()).getSeconds();
                dto.setVideoElapsedSeconds(Math.max(0, elapsed));
            }
        }

        return dto;
    }
}

