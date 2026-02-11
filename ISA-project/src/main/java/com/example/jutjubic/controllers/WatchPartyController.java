package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.CreateWatchPartyDto;
import com.example.jutjubic.dto.WatchPartyChatMessageDto;
import com.example.jutjubic.dto.WatchPartyRoomDto;
import com.example.jutjubic.models.User;
import com.example.jutjubic.services.WatchPartyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST kontroler za Watch Party funkcionalnost.
 */
@RestController
@RequestMapping("/api/watch-party")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:8080"})
public class WatchPartyController {

    private final WatchPartyService watchPartyService;

    public WatchPartyController(WatchPartyService watchPartyService) {
        this.watchPartyService = watchPartyService;
    }

    /**
     * Kreira novu Watch Party sobu.
     * Zahteva autentifikaciju.
     */
    @PostMapping("/create")
    public ResponseEntity<WatchPartyRoomDto> createRoom(
            @RequestBody CreateWatchPartyDto dto,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WatchPartyRoomDto room = watchPartyService.createRoom(dto, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    /**
     * Dobija sobu po ID-u.
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<WatchPartyRoomDto> getRoomById(@PathVariable UUID roomId) {
        return watchPartyService.getRoomById(roomId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Dobija sobu po kodu za pridruživanje.
     */
    @GetMapping("/code/{roomCode}")
    public ResponseEntity<WatchPartyRoomDto> getRoomByCode(@PathVariable String roomCode) {
        return watchPartyService.getRoomByCode(roomCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Dobija sve aktivne javne sobe.
     */
    @GetMapping("/public")
    public ResponseEntity<List<WatchPartyRoomDto>> getPublicRooms() {
        List<WatchPartyRoomDto> rooms = watchPartyService.getPublicRooms();
        return ResponseEntity.ok(rooms);
    }

    /**
     * Dobija sve sobe koje je kreirao trenutni korisnik.
     */
    @GetMapping("/my-rooms")
    public ResponseEntity<List<WatchPartyRoomDto>> getMyRooms(
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<WatchPartyRoomDto> rooms = watchPartyService.getRoomsByOwner(user.getId());
        return ResponseEntity.ok(rooms);
    }

    /**
     * Pridruživanje sobi po kodu.
     * Vraća informacije o sobi ako je aktivna.
     */
    @PostMapping("/join/{roomCode}")
    public ResponseEntity<?> joinRoom(@PathVariable String roomCode) {
        return watchPartyService.getRoomByCode(roomCode)
                .map(room -> {
                    if (!room.isActive()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Room is no longer active"));
                    }
                    return ResponseEntity.ok(room);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Postavlja trenutni video u sobi.
     * Samo vlasnik sobe može menjati video.
     */
    @PostMapping("/{roomCode}/video/{videoId}")
    public ResponseEntity<?> setCurrentVideo(
            @PathVariable String roomCode,
            @PathVariable UUID videoId,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            WatchPartyRoomDto room = watchPartyService.setCurrentVideo(roomCode, videoId, user.getId());
            return ResponseEntity.ok(room);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Zatvara Watch Party sobu.
     * Samo vlasnik može zatvoriti sobu.
     */
    @DeleteMapping("/{roomCode}")
    public ResponseEntity<?> closeRoom(
            @PathVariable String roomCode,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            watchPartyService.closeRoom(roomCode, user.getId());
            return ResponseEntity.ok(Map.of("message", "Room closed successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Dobija istoriju chat poruka za sobu.
     */
    @GetMapping("/{roomCode}/messages")
    public ResponseEntity<List<WatchPartyChatMessageDto>> getChatHistory(@PathVariable String roomCode) {
        try {
            List<WatchPartyChatMessageDto> messages = watchPartyService.getMessagesForRoom(roomCode);
            return ResponseEntity.ok(messages);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Proverava da li je korisnik vlasnik sobe.
     */
    @GetMapping("/{roomCode}/is-owner")
    public ResponseEntity<Map<String, Boolean>> isOwner(
            @PathVariable String roomCode,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.ok(Map.of("isOwner", false));
        }

        boolean isOwner = watchPartyService.isRoomOwner(roomCode, user.getId());
        return ResponseEntity.ok(Map.of("isOwner", isOwner));
    }

    /**
     * Dobija listu aktivnih članova u sobi.
     */
    @GetMapping("/{roomCode}/members")
    public ResponseEntity<Map<String, Object>> getActiveMembers(@PathVariable String roomCode) {
        java.util.List<String> members = watchPartyService.getActiveMembers(roomCode);
        int count = watchPartyService.getMemberCount(roomCode);
        return ResponseEntity.ok(Map.of(
            "members", members,
            "count", count
        ));
    }
}

