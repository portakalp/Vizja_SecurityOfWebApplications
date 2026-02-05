package com.securenotes.controller;

import com.securenotes.config.RateLimitConfig;
import com.securenotes.dto.AuthResponse;
import com.securenotes.dto.LoginRequest;
import com.securenotes.dto.RefreshTokenRequest;
import com.securenotes.dto.RegisterRequest;
import com.securenotes.dto.UserResponse;
import com.securenotes.exception.BadRequestException;
import com.securenotes.security.UserDetailsImpl;
import com.securenotes.service.AuthService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 * Handles registration, login, token refresh, and logout.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimitConfig rateLimitConfig;

    public AuthController(AuthService authService, RateLimitConfig rateLimitConfig) {
        this.authService = authService;
        this.rateLimitConfig = rateLimitConfig;
    }

    /**
     * Registers a new user.
     * POST /auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates user and returns tokens.
     * Rate limited to prevent brute force attacks.
     * POST /auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        Bucket bucket = rateLimitConfig.resolveBucketForLogin(clientIp);

        if (!bucket.tryConsume(1)) {
            throw new BadRequestException("Too many login attempts. Please try again later.");
        }

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refreshes access token using refresh token.
     * Implements token rotation for security.
     * POST /auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Logs out user by invalidating refresh token.
     * POST /auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Logs out user from all devices.
     * Requires authentication.
     * POST /auth/logout-all
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        authService.logoutAll(userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Extracts client IP address from request.
     * Handles proxied requests via X-Forwarded-For header.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
