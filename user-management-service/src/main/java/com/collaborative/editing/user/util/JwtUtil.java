package com.collaborative.editing.user.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private Long expiration;
    
    private SecretKey getSigningKey() {
        // Ensure the secret is at least 256 bits (32 characters) for HS256
        String key = secret;
        if (key.length() < 32) {
            // Pad or repeat to ensure minimum length
            key = key.repeat((32 / key.length()) + 1).substring(0, 32);
        }
        return Keys.hmacShaKeyFor(key.getBytes());
    }
    
    public String generateToken(Long userId, String username) {
        logger.debug("Generating JWT token for user: ID={}, username={}", userId, username);
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        String token = createToken(claims, userId.toString());
        logger.debug("JWT token generated successfully for user ID={}", userId);
        return token;
    }
    
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    public Long getUserIdFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }
        return Long.parseLong(claims.getSubject());
    }
    
    public String getUsernameFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("username", String.class);
    }
    
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }
    
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public Boolean isTokenExpired(String token) {
        try {
            final Date expiration = getExpirationDateFromToken(token);
            boolean expired = expiration.before(new Date());
            if (expired) {
                logger.debug("Token is expired");
            }
            return expired;
        } catch (Exception e) {
            logger.warn("Error checking token expiration", e);
            return true;
        }
    }
    
    public Boolean validateToken(String token, Long userId) {
        try {
            final Long tokenUserId = getUserIdFromToken(token);
            boolean valid = tokenUserId.equals(userId) && !isTokenExpired(token);
            if (!valid) {
                logger.debug("Token validation failed for user ID={}", userId);
            }
            return valid;
        } catch (Exception e) {
            logger.warn("Error validating token for user ID={}", userId, e);
            return false;
        }
    }
    
    public Boolean validateToken(String token) {
        try {
            boolean valid = !isTokenExpired(token);
            if (!valid) {
                logger.debug("Token validation failed: token is expired");
            }
            return valid;
        } catch (Exception e) {
            logger.warn("Error validating token", e);
            return false;
        }
    }
}

