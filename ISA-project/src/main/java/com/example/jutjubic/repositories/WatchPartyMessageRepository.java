package com.example.jutjubic.repositories;

import com.example.jutjubic.models.WatchPartyMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository za Watch Party chat poruke.
 */
@Repository
public interface WatchPartyMessageRepository extends JpaRepository<WatchPartyMessage, UUID> {

    /**
     * Pronalazi sve poruke za određenu sobu, sortirane po vremenu kreiranja.
     */
    List<WatchPartyMessage> findByRoomIdOrderByCreatedAtAsc(UUID roomId);

    /**
     * Pronalazi poslednjih N poruka za sobu.
     */
    List<WatchPartyMessage> findTop100ByRoomIdOrderByCreatedAtDesc(UUID roomId);

    /**
     * Briše sve poruke za sobu.
     */
    void deleteByRoomId(UUID roomId);
}

