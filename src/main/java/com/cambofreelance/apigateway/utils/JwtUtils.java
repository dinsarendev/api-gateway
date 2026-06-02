package com.cambofreelance.apigateway.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JwtUtils {

    @Value("${authentication.jwtSecret}")
    private String jwtSecret;

    @Value("${authentication.jwtExpiration}")
    private int jwtExpirationMs;

    @Value("${authentication.adminJwtExpiration:3600000}")
    private int adminJwtExpirationMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // ── Token generation ──────────────────────────────────────────────────────

    public String generateAdminToken(String username, List<String> roles) {
        return generateAdminToken(username, roles, List.of());
    }

    public String generateAdminToken(String username, List<String> roles, List<String> permissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles",       roles);
        claims.put("permissions", permissions);
        claims.put("admin",       true);
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(username)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + adminJwtExpirationMs))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    // ── Token reading ─────────────────────────────────────────────────────────

    public String getUserIdFromJwtToken(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (Throwable e) {
            log.error("Error while parsing JWT token: {}", e.getMessage());
            return "";
        }
    }

    public String getDataTokenAndKey(String token, String key) {
        try {
            return parseClaims(token).get(key, String.class);
        } catch (Throwable e) {
            log.info("Error while parsing JWT token: {}", e.getMessage());
            return "";
        }
    }

    public boolean isAdminToken(String token) {
        try {
            return Boolean.TRUE.equals(parseClaims(token).get("admin", Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        try {
            Object roles = parseClaims(token).get("roles");
            if (roles instanceof List<?> list) return (List<String>) list;
            if (roles instanceof String s && !s.isBlank()) return Arrays.asList(s.split(","));
        } catch (Throwable ignored) {}
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        try {
            Object perms = parseClaims(token).get("permissions");
            if (perms instanceof List<?> list) return (List<String>) list;
            if (perms instanceof String s && !s.isBlank()) return Arrays.asList(s.split(","));
        } catch (Throwable ignored) {}
        return List.of();
    }

    // ── Token validation ──────────────────────────────────────────────────────

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
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

    // ── Private ───────────────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
}