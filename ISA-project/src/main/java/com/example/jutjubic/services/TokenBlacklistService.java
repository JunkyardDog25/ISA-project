package com.example.jutjubic.services;

import com.example.jutjubic.models.BlacklistedToken;
import com.example.jutjubic.repositories.BlacklistedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    public TokenBlacklistService(BlacklistedTokenRepository blacklistedTokenRepository) {
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    /**
     * Blacklist a token so it can no longer be used for authentication.
     *
     * @param token     The JWT token to blacklist
     * @param expiresAt When the token would naturally expire
     */
    @Transactional
    public void blacklistToken(String token, LocalDateTime expiresAt) {
        if (!blacklistedTokenRepository.existsByToken(token)) {
            BlacklistedToken blacklistedToken = new BlacklistedToken(token, expiresAt);
            blacklistedTokenRepository.save(blacklistedToken);
            logger.info("Token blacklisted successfully");
        }
    }

    /**
     * Check if a token has been blacklisted.
     *
     * @param token The JWT token to check
     * @return true if the token is blacklisted, false otherwise
     */
    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokenRepository.existsByToken(token);
    }

    /**
     * Scheduled task to clean up expired blacklisted tokens.
     * Runs every hour to remove tokens that have naturally expired.
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void cleanupExpiredTokens() {
        logger.debug("Cleaning up expired blacklisted tokens");
        blacklistedTokenRepository.deleteExpiredTokens(LocalDateTime.now());
    }
}
