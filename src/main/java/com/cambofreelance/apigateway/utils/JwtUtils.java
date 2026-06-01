package com.cambofreelance.apigateway.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtUtils {

    @Value("${authentication.jwtSecret}")
    private String jwtSecret;

    @Value("${authentication.jwtExpiration}")
    private int jwtExpirationMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String getUserIdFromJwtToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
            return claims.getSubject();
        } catch (Throwable e) {
            log.error("Error while parsing JWT token: {}", e.getMessage());
            return "";
        }
    }

    public String getDataTokenAndKey(String token, String key) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
            return claims.get(key, String.class);
        } catch (Throwable e) {
            log.info("Error while parsing JWT token: {}", e.getMessage());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> getRolesFromToken(String token) {
        try {
            Object roles = Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody().get("roles");
            if (roles instanceof java.util.List<?> list) return (java.util.List<String>) list;
            if (roles instanceof String s && !s.isBlank())
                return java.util.Arrays.asList(s.split(","));
        } catch (Throwable ignored) {}
        return java.util.List.of();
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> getPermissionsFromToken(String token) {
        try {
            Object perms = Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody().get("permissions");
            if (perms instanceof java.util.List<?> list) return (java.util.List<String>) list;
            if (perms instanceof String s && !s.isBlank())
                return java.util.Arrays.asList(s.split(","));
        } catch (Throwable ignored) {}
        return java.util.List.of();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(authToken);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}
