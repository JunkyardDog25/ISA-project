package com.example.jutjubic.services;

import com.example.jutjubic.dto.CreateWatchPartyDto;
import com.example.jutjubic.dto.WatchPartyChatMessageDto;
import com.example.jutjubic.dto.WatchPartyRoomDto;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.models.WatchPartyMessage;
import com.example.jutjubic.models.WatchPartyRoom;
import com.example.jutjubic.repositories.UserRepository;
import com.example.jutjubic.repositories.VideoRepository;
import com.example.jutjubic.repositories.WatchPartyMessageRepository;
import com.example.jutjubic.repositories.WatchPartyRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Servis za upravljanje Watch Party sobama.
 */
@Service
public class WatchPartyService {

    private final WatchPartyRoomRepository watchPartyRoomRepository;
    private final WatchPartyMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    /**
     * Mapa za praćenje aktivnih članova po sobi (roomCode -> Set of userIds).
     * Koristi ConcurrentHashMap sa synchroniziranim Set-om za thread-safety.
     */
    private final Map<String, Set<String>> roomMembers = new ConcurrentHashMap<>();

    public WatchPartyService(WatchPartyRoomRepository watchPartyRoomRepository,
                              WatchPartyMessageRepository messageRepository,
                              UserRepository userRepository,
                              VideoRepository videoRepository) {
        this.watchPartyRoomRepository = watchPartyRoomRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
    }

    /**
     * Kreira novu Watch Party sobu.
     */
    @Transactional
    public WatchPartyRoomDto createRoom(CreateWatchPartyDto dto, UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        WatchPartyRoom room = new WatchPartyRoom(
                dto.getName(),
                dto.getDescription(),
                owner,
                dto.isPublic()
        );

        // Osiguraj da je roomCode jedinstven
        while (watchPartyRoomRepository.existsByRoomCode(room.getRoomCode())) {
            room.setRoomCode(generateRoomCode());
        }

        WatchPartyRoom savedRoom = watchPartyRoomRepository.save(room);

        // Inicijalizuj Set za praćenje članova
        roomMembers.put(savedRoom.getRoomCode(), ConcurrentHashMap.newKeySet());

        return WatchPartyRoomDto.fromEntity(savedRoom);
    }

    /**
     * Dobija sobu po ID-u.
     */
    public Optional<WatchPartyRoomDto> getRoomById(UUID roomId) {
        return watchPartyRoomRepository.findById(roomId)
                .map(room -> {
                    room.setMemberCount(getMemberCount(room.getRoomCode()));
                    return WatchPartyRoomDto.fromEntity(room);
                });
    }

    /**
     * Dobija sobu po kodu za pridruživanje.
     */
    public Optional<WatchPartyRoomDto> getRoomByCode(String roomCode) {
        return watchPartyRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .map(room -> {
                    room.setMemberCount(getMemberCount(room.getRoomCode()));
                    return WatchPartyRoomDto.fromEntity(room);
                });
    }

    /**
     * Dobija sve aktivne javne sobe.
     */
    public List<WatchPartyRoomDto> getPublicRooms() {
        return watchPartyRoomRepository.findByActiveTrueAndIsPublicTrue().stream()
                .map(room -> {
                    room.setMemberCount(getMemberCount(room.getRoomCode()));
                    return WatchPartyRoomDto.fromEntity(room);
                })
                .collect(Collectors.toList());
    }

    /**
     * Dobija sve sobe koje je kreirao određeni korisnik.
     */
    public List<WatchPartyRoomDto> getRoomsByOwner(UUID ownerId) {
        return watchPartyRoomRepository.findByOwnerIdAndActiveTrue(ownerId).stream()
                .map(room -> {
                    room.setMemberCount(getMemberCount(room.getRoomCode()));
                    return WatchPartyRoomDto.fromEntity(room);
                })
                .collect(Collectors.toList());
    }

    /**
     * Ažurira trenutni video u sobi.
     * Samo vlasnik sobe može menjati video.
     */
    @Transactional
    public WatchPartyRoomDto setCurrentVideo(String roomCode, UUID videoId, UUID userId) {
        WatchPartyRoom room = watchPartyRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Only room owner can change the video");
        }

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        room.setCurrentVideo(video);
        room.setVideoStartedAt(LocalDateTime.now()); // Postavi vrijeme početka videa
        WatchPartyRoom savedRoom = watchPartyRoomRepository.save(room);
        savedRoom.setMemberCount(getMemberCount(savedRoom.getRoomCode()));

        return WatchPartyRoomDto.fromEntity(savedRoom);
    }

    /**
     * Zatvara Watch Party sobu.
     * Samo vlasnik može zatvoriti sobu.
     */
    @Transactional
    public void closeRoom(String roomCode, UUID userId) {
        WatchPartyRoom room = watchPartyRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Only room owner can close the room");
        }

        room.setActive(false);
        watchPartyRoomRepository.save(room);

        // Ukloni iz praćenja članova
        roomMembers.remove(roomCode.toUpperCase());
    }

    /**
     * Registruje pridruživanje člana sobi.
     * @param roomCode Kod sobe
     * @param oderId ID korisnika (može biti UUID ili session ID za goste)
     * @return Trenutni broj članova u sobi
     */
    public int memberJoined(String roomCode, String oderId) {
        roomCode = roomCode.toUpperCase();
        roomMembers.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(oderId);
        return getMemberCount(roomCode);
    }

    /**
     * Registruje napuštanje člana iz sobe.
     * @param roomCode Kod sobe
     * @param oderId ID korisnika
     * @return Trenutni broj članova u sobi
     */
    public int memberLeft(String roomCode, String oderId) {
        roomCode = roomCode.toUpperCase();
        Set<String> members = roomMembers.get(roomCode);
        if (members != null) {
            members.remove(oderId);
        }
        return getMemberCount(roomCode);
    }

    /**
     * Dobija trenutni broj članova u sobi.
     */
    public int getMemberCount(String roomCode) {
        Set<String> members = roomMembers.get(roomCode.toUpperCase());
        return members != null ? members.size() : 0;
    }

    /**
     * Proverava da li je korisnik vlasnik sobe.
     */
    public boolean isRoomOwner(String roomCode, UUID userId) {
        return watchPartyRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .map(room -> room.getOwner().getId().equals(userId))
                .orElse(false);
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

    // ==================== CHAT METHODS ====================

    /**
     * Čuva chat poruku u bazu.
     */
    @Transactional
    public WatchPartyChatMessageDto saveMessage(String roomCode, UUID senderId, String senderUsername,
                                                  String content, WatchPartyMessage.MessageType messageType) {
        WatchPartyRoom room = watchPartyRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        User sender = null;
        if (senderId != null) {
            sender = userRepository.findById(senderId).orElse(null);
        }

        WatchPartyMessage message = new WatchPartyMessage(room, sender, senderUsername, content, messageType);
        WatchPartyMessage saved = messageRepository.save(message);

        return WatchPartyChatMessageDto.fromEntity(saved);
    }

    /**
     * Dobija sve poruke za sobu (za učitavanje istorije).
     */
    public List<WatchPartyChatMessageDto> getMessagesForRoom(String roomCode) {
        WatchPartyRoom room = watchPartyRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        List<WatchPartyMessage> messages = messageRepository.findByRoomIdOrderByCreatedAtAsc(room.getId());

        return messages.stream()
                .map(WatchPartyChatMessageDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Briše sve poruke za sobu (poziva se kada se soba zatvori).
     */
    @Transactional
    public void deleteMessagesForRoom(String roomCode) {
        WatchPartyRoom room = watchPartyRoomRepository.findByRoomCode(roomCode.toUpperCase()).orElse(null);
        if (room != null) {
            messageRepository.deleteByRoomId(room.getId());
        }
    }
}

