package com.docket.app.service;

import com.docket.app.entity.User;
import com.docket.app.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {
    private static final String CLAIM_TOKEN_VERSION = "tv";
    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_EMAIL_VERIFIED = "emailVerified";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-ttl-ms}")
    private long accessTokenTtlMs;

    private Key getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // sessionId is null for a token that has no refresh session behind it.
    public String generateAccessToken(User user, UUID sessionId) {
        JwtBuilder builder = Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenTtlMs))
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .claim(CLAIM_EMAIL_VERIFIED, user.getEmailVerifiedAt() != null);

        if (sessionId != null) {
            builder.claim(CLAIM_SESSION_ID, sessionId.toString());
        }

        return builder
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractSessionId(String token) {
        String sessionId = extractAllClaims(token).get(CLAIM_SESSION_ID, String.class);
        return sessionId == null ? null : UUID.fromString(sessionId);
    }

    // A token is only good while its version matches the user's current one. Tokens
    // from before versions existed have no claim, and are rejected like stale ones.
    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        Integer tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date())
                && userDetails instanceof AuthenticatedUser user
                && tokenVersion != null
                && tokenVersion == user.getTokenVersion();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
