package com.example.jutjubic.repositories;

import com.example.jutjubic.models.WatchPartyRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository za Watch Party sobe.
 */
@Repository
public interface WatchPartyRoomRepository extends JpaRepository<WatchPartyRoom, UUID> {

    /**
     * Pronalazi sobu po jedinstvenom kodu za pridruživanje.
     */
    Optional<WatchPartyRoom> findByRoomCode(String roomCode);

    /**
     * Pronalazi sve aktivne sobe.
     */
    List<WatchPartyRoom> findByActiveTrue();

    /**
     * Pronalazi sve aktivne i javne sobe.
     */
    List<WatchPartyRoom> findByActiveTrueAndIsPublicTrue();

    /**
     * Pronalazi sve sobe koje je kreirao određeni korisnik.
     */
    List<WatchPartyRoom> findByOwnerId(UUID ownerId);

    /**
     * Pronalazi sve aktivne sobe koje je kreirao određeni korisnik.
     */
    List<WatchPartyRoom> findByOwnerIdAndActiveTrue(UUID ownerId);

    /**
     * Proverava da li kod sobe već postoji.
     */
    boolean existsByRoomCode(String roomCode);
}

