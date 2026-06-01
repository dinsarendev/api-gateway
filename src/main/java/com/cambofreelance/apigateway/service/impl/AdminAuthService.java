package com.cambofreelance.apigateway.service.impl;

import com.cambofreelance.apigateway.models.AdminRefreshToken;
import com.cambofreelance.apigateway.repositories.AdminRefreshTokenRepository;
import com.cambofreelance.apigateway.repositories.AdminRoleRepository;
import com.cambofreelance.apigateway.repositories.AdminUserRepository;
import com.cambofreelance.apigateway.security.SecurityService.UnauthorizedException;
import com.cambofreelance.apigateway.utils.JwtUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserRepository userRepository;
    private final AdminRoleRepository roleRepository;
    private final AdminRefreshTokenRepository refreshTokenRepository;
    private final JwtUtils jwtUtils;

    @Value("${authentication.refreshTokenExpireDays:7}")
    private int refreshTokenExpireDays;

    // ── Login ─────────────────────────────────────────────────────────────────

    public Mono<TokenPair> login(String username, String password) {
        return userRepository.findActiveByUsername(username)
            .publishOn(Schedulers.boundedElastic())
            .filter(u -> BCrypt.checkpw(password, u.getPasswordHash()))
            .switchIfEmpty(Mono.error(new UnauthorizedException("Invalid username or password")))
            .flatMap(user ->
                roleRepository.findByUserId(user.getId())
                    .map(r -> r.getName())
                    .collectList()
                    .flatMap(roles -> {
                        String accessToken  = jwtUtils.generateAdminToken(user.getUsername(), roles);
                        String rawRefresh   = newRefreshToken();
                        AdminRefreshToken rt = AdminRefreshToken.builder()
                            .userId(user.getId())
                            .tokenHash(sha256(rawRefresh))
                            .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpireDays))
                            .revoked(false)
                            .createdAt(LocalDateTime.now())
                            .build();
                        return refreshTokenRepository.save(rt)
                            .then(userRepository.touchLastLogin(user.getId(), LocalDateTime.now()))
                            .thenReturn(new TokenPair(
                                accessToken, rawRefresh,
                                3600, user.getUsername(), user.getFullName(), roles
                            ));
                    })
            );
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    public Mono<TokenPair> refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        return refreshTokenRepository.findValid(hash)
            .switchIfEmpty(Mono.error(new UnauthorizedException("Invalid or expired refresh token")))
            .flatMap(rt ->
                refreshTokenRepository.revokeById(rt.getId())        // rotate
                    .then(userRepository.findById(rt.getUserId()))
                    .switchIfEmpty(Mono.error(new UnauthorizedException("User not found")))
                    .flatMap(user ->
                        roleRepository.findByUserId(user.getId())
                            .map(r -> r.getName())
                            .collectList()
                            .flatMap(roles -> {
                                String newAccess  = jwtUtils.generateAdminToken(user.getUsername(), roles);
                                String rawNewRefresh = newRefreshToken();
                                AdminRefreshToken newRt = AdminRefreshToken.builder()
                                    .userId(user.getId())
                                    .tokenHash(sha256(rawNewRefresh))
                                    .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpireDays))
                                    .revoked(false)
                                    .createdAt(LocalDateTime.now())
                                    .build();
                                return refreshTokenRepository.save(newRt)
                                    .thenReturn(new TokenPair(
                                        newAccess, rawNewRefresh,
                                        3600, user.getUsername(), user.getFullName(), roles
                                    ));
                            })
                    )
            );
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    public Mono<Void> logout(String rawRefreshToken) {
        return refreshTokenRepository.revokeByHash(sha256(rawRefreshToken)).then();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String newRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "")
             + UUID.randomUUID().toString().replace("-", "");
    }

    public String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes()));
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    // ── Response record ───────────────────────────────────────────────────────

    public record TokenPair(
        @JsonProperty("access_token")  String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in")    int expiresIn,
        String username,
        @JsonProperty("full_name")     String fullName,
        List<String> roles
    ) {}
}