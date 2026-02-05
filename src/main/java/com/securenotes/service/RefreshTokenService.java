package com.securenotes.service;

import com.securenotes.entity.RefreshToken;
import com.securenotes.entity.User;
import com.securenotes.exception.TokenRefreshException;
import com.securenotes.repository.RefreshTokenRepository;
import com.securenotes.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for managing refresh tokens.
 * Implements token rotation and server-side invalidation for secure logout.
 */
@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    /**
     * Creates and persists a new refresh token for the user.
     *
     * @param user The user to create the token for
     * @param tokenValue The JWT refresh token value
     * @return The created RefreshToken entity
     */
    @Transactional
    public RefreshToken createRefreshToken(User user, String tokenValue) {
        Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshTokenExpiration());
        
        RefreshToken refreshToken = new RefreshToken(tokenValue, user, expiresAt);
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Validates a refresh token and returns it if valid.
     * Throws exception if token is expired, revoked, or not found.
     *
     * @param token The refresh token string
     * @return The valid RefreshToken entity
     * @throws TokenRefreshException if token is invalid
     */
    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new TokenRefreshException("Refresh token not found"));

        if (refreshToken.isExpiredOrRevoked()) {
            throw new TokenRefreshException("Refresh token is expired or revoked");
        }

        return refreshToken;
    }

    /**
     * Implements refresh token rotation.
     * Revokes the old token and creates a new one.
     *
     * @param oldToken The old refresh token to rotate
     * @param newTokenValue The new JWT refresh token value
     * @return The new RefreshToken entity
     */
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken, String newTokenValue) {
        refreshTokenRepository.revokeByToken(oldToken.getToken());
        logger.debug("Revoked old refresh token during rotation");
        
        return createRefreshToken(oldToken.getUser(), newTokenValue);
    }

    /**
     * Revokes all refresh tokens for a user (logout from all devices).
     *
     * @param userId The user ID
     */
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        logger.info("Revoked all refresh tokens for user: {}", userId);
    }

    /**
     * Revokes a specific refresh token (single device logout).
     *
     * @param token The refresh token to revoke
     */
    @Transactional
    public void revokeToken(String token) {
        refreshTokenRepository.revokeByToken(token);
        logger.debug("Revoked refresh token");
    }

    /**
     * Cleanup job to delete expired tokens.
     */
    @Transactional
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens();
        logger.info("Deleted expired refresh tokens");
    }
}
