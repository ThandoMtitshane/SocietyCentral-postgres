package com.societycentral.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/**
 * Handles JWT creation, parsing, and validation.
 * <p>
 * Configuration (see application.properties.example):
 * <pre>
 * jwt.secret=...   (a long, random, Base64-encoded string - keep this SECRET)
 * jwt.expiration-ms=...  (token lifetime in milliseconds, e.g. 86400000 = 24h)
 * </pre>
 */
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("authorities", java.util.List.of("ROLE_" + role))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenValid(String token, String expectedEmail) {
        try {
            String email = extractEmail(token);
            return email.equals(expectedEmail) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
    public String generateRSVPToken(
            String studentNumber,
            String eventId,
            Integer advertisementVersion,
            Date expiry
    ) {

        Date now = new Date();

        return Jwts.builder()
                .subject(studentNumber)
                .claim("type", "RSVP_INVITE")
                .claim("eventId", eventId)
                .claim("advertisementVersion", advertisementVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parses and validates an RSVP invitation token.
     *
     * @param token the RSVP JWT token
     * @return Claims containing studentNumber (subject), eventId, advertisementVersion
     * @throws io.jsonwebtoken.JwtException if the token is invalid or expired
     */
    public Claims parseRSVPToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String type = claims.get("type", String.class);
        if (!"RSVP_INVITE".equals(type)) {
            throw new io.jsonwebtoken.MalformedJwtException("Token is not an RSVP invitation.");
        }

        return claims;
    }
}