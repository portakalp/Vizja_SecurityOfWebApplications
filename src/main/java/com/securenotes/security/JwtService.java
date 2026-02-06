package com.securenotes.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Service for JWT token generation and validation.
 * Implements stateless authentication with access and refresh tokens.
 */
@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpiration;

    /**
     * Extracts username (email) from JWT token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts user ID from JWT token.
     */
    public UUID extractUserId(String token) {
        String userId = extractClaim(token, claims -> claims.get("userId", String.class));
        return userId != null ? UUID.fromString(userId) : null;
    }

    /**
     * Extracts expiration date from JWT token.
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic claim extractor.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generates an access token for the user.
     */
    public String generateAccessToken(UserDetails userDetails, UUID userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("type", "access");
        claims.put("roles", userDetails.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .toList());
        return buildToken(claims, userDetails.getUsername(), accessTokenExpiration);
    }

    /**
     * Generates a refresh token for the user.
     */
    public String generateRefreshToken(UserDetails userDetails, UUID userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("type", "refresh");
        return buildToken(claims, userDetails.getUsername(), refreshTokenExpiration);
    }

    /**
     * Builds a JWT token with specified claims and expiration.
     */
    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validates the JWT token against user details.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (Exception e) {
            logger.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates token structure and signature without user details.
     */
    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (SignatureException e) {
            logger.warn("Invalid JWT signature");
        } catch (MalformedJwtException e) {
            logger.warn("Invalid JWT token");
        } catch (ExpiredJwtException e) {
            logger.debug("JWT token is expired");
        } catch (UnsupportedJwtException e) {
            logger.warn("JWT token is unsupported");
        } catch (IllegalArgumentException e) {
            logger.warn("JWT claims string is empty");
        }
        return false;
    }

    /**
     * Checks if the token is expired.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts all claims from the token.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Gets the signing key from the secret.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Returns access token expiration in milliseconds.
     */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    /**
     * Returns refresh token expiration in milliseconds.
     */
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
