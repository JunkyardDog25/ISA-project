package com.example.jutjubic.repositories;

import com.example.jutjubic.models.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    boolean existsByToken(String token);

    @Modifying
    @Query("DELETE FROM BlacklistedToken b WHERE b.expiresAt < :now")
    void deleteExpiredTokens(LocalDateTime now);
}
