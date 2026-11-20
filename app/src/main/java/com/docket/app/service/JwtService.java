package com.docket.app.service;

import com.docket.app.entity.ServiceAccount;
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
    private static final String CLAIM_CLIENT_ID = "cid";
    private static final String CLAIM_TOKEN_TYPE = "typ";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-ttl-ms}")
    private long accessTokenTtlMs;

    @Value("${jwt.service-account-token-ttl-ms:3600000}") // default 1 hour
    private long serviceAccountTokenTtlMs;

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
                .claim(CLAIM_EMAIL_VERIFIED, user.getEmailVerifiedAt() != null)
                .claim(CLAIM_TOKEN_TYPE, "user");

        if (sessionId != null) {
            builder.claim(CLAIM_SESSION_ID, sessionId.toString());
        }

        return builder
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateServiceAccountToken(ServiceAccount serviceAccount) {
        return Jwts.builder()
                .setSubject(serviceAccount.getClientId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + serviceAccountTokenTtlMs))
                .claim(CLAIM_CLIENT_ID, serviceAccount.getClientId())
                .claim(CLAIM_TOKEN_TYPE, "service_account")
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractClientId(String token) {
        return extractAllClaims(token).get(CLAIM_CLIENT_ID, String.class);
    }

    public String extractTokenType(String token) {
        return extractAllClaims(token).get(CLAIM_TOKEN_TYPE, String.class);
    }

    public UUID extractSessionId(String token) {
        String sessionId = extractAllClaims(token).get(CLAIM_SESSION_ID, String.class);
        return sessionId == null ? null : UUID.fromString(sessionId);
    }

    // A token is only good while its version matches the user's current one. Tokens
    // from before versions existed have no claim, and are rejected like stale ones.
    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        if (claims.get("purpose") != null) {
            return false;
        }
        if (!"user".equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            return false;
        }
        Integer tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date())
                && userDetails instanceof AuthenticatedUser user
                && tokenVersion != null
                && tokenVersion == user.getTokenVersion();
    }

    public boolean isServiceAccountTokenValid(String token, ServiceAccount serviceAccount) {
        Claims claims = extractAllClaims(token);
        if (!"service_account".equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            return false;
        }
        String clientId = claims.get(CLAIM_CLIENT_ID, String.class);
        return serviceAccount.getClientId().equals(clientId)
                && claims.getExpiration().after(new Date());
    }

    public String generateTwoFactorTempToken(User user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 5 * 60 * 1000)) // 5 minutes
                .claim("purpose", "2fa_pending")
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String validateTwoFactorTempToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            if (!"2fa_pending".equals(claims.get("purpose", String.class))) {
                return null;
            }
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
