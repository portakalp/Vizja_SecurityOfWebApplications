package com.securenotes.service;

import com.securenotes.dto.AuthResponse;
import com.securenotes.dto.LoginRequest;
import com.securenotes.dto.RefreshTokenRequest;
import com.securenotes.dto.RegisterRequest;
import com.securenotes.dto.UserResponse;
import com.securenotes.entity.RefreshToken;
import com.securenotes.entity.User;
import com.securenotes.exception.BadRequestException;
import com.securenotes.security.JwtService;
import com.securenotes.security.UserDetailsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service handling authentication operations.
 * Implements login, registration, token refresh, and logout.
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager,
                      UserService userService,
                      RefreshTokenService refreshTokenService,
                      JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
    }

    /**
     * Registers a new user.
     *
     * @param request Registration request
     * @return UserResponse with created user info
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        return userService.registerUser(request);
    }

    /**
     * Authenticates user and returns access and refresh tokens.
     *
     * @param request Login credentials
     * @return AuthResponse with tokens
     * @throws BadCredentialsException if credentials are invalid
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            
            String accessToken = jwtService.generateAccessToken(userDetails, userDetails.getId());
            String refreshToken = jwtService.generateRefreshToken(userDetails, userDetails.getId());

            User user = userService.findByEmail(userDetails.getEmail())
                    .orElseThrow(() -> new BadRequestException("User not found"));
            
            refreshTokenService.createRefreshToken(user, refreshToken);

            logger.info("User logged in successfully: {}", userDetails.getEmail());

            return new AuthResponse(accessToken, refreshToken, jwtService.getAccessTokenExpiration());
        } catch (BadCredentialsException e) {
            logger.warn("Failed login attempt for email: {}", request.getEmail());
            throw e;
        }
    }

    /**
     * Refreshes access token using a valid refresh token.
     * Implements token rotation - old refresh token is invalidated.
     *
     * @param request Refresh token request
     * @return New AuthResponse with fresh tokens
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken oldRefreshToken = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        User user = oldRefreshToken.getUser();

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        
        String newAccessToken = jwtService.generateAccessToken(userDetails, user.getId());
        String newRefreshToken = jwtService.generateRefreshToken(userDetails, user.getId());

        refreshTokenService.rotateRefreshToken(oldRefreshToken, newRefreshToken);

        logger.debug("Token refreshed for user: {}", user.getEmail());

        return new AuthResponse(newAccessToken, newRefreshToken, jwtService.getAccessTokenExpiration());
    }

    /**
     * Logs out user by revoking their refresh token.
     *
     * @param refreshToken The refresh token to revoke
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revokeToken(refreshToken);
        logger.debug("User logged out, refresh token revoked");
    }

    /**
     * Logs out user from all devices by revoking all refresh tokens.
     *
     * @param userId The user ID
     */
    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokenService.revokeAllUserTokens(userId);
        logger.info("User {} logged out from all devices", userId);
    }
}
