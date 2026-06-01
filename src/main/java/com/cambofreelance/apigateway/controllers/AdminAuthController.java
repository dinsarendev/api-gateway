package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.repositories.AdminRoleRepository;
import com.cambofreelance.apigateway.repositories.AdminUserRepository;
import com.cambofreelance.apigateway.security.SecurityService.UnauthorizedException;
import com.cambofreelance.apigateway.service.impl.AdminAuthService;
import com.cambofreelance.apigateway.service.impl.AdminAuthService.TokenPair;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService authService;
    private final AdminUserRepository userRepository;
    private final AdminRoleRepository roleRepository;

    @PostMapping("/login")
    public Mono<ResponseEntity<TokenPair>> login(@RequestBody LoginRequest req) {
        return authService.login(req.username(), req.password())
            .map(ResponseEntity::ok)
            .onErrorResume(UnauthorizedException.class,
                e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<TokenPair>> refresh(@RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken())
            .map(ResponseEntity::ok)
            .onErrorResume(UnauthorizedException.class,
                e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, Object>>> logout(@RequestBody RefreshRequest req) {
        return authService.logout(req.refreshToken())
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of("message", "Logged out successfully")));
    }

    @GetMapping("/profile")
    public Mono<ResponseEntity<Map<String, Object>>> profile(ServerWebExchange exchange) {
        String username = exchange.getRequest().getHeaders().getFirst("X-Admin-User");
        if (username == null) return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

        return userRepository.findActiveByUsername(username)
            .flatMap(user -> roleRepository.findByUserId(user.getId())
                .map(r -> r.getName())
                .collectList()
                .map(roles -> ResponseEntity.ok(Map.<String, Object>of(
                    "username",  user.getUsername(),
                    "full_name", user.getFullName() != null ? user.getFullName() : "",
                    "email",     user.getEmail() != null ? user.getEmail() : "",
                    "roles",     roles
                )))
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    public record LoginRequest(String username, String password) {}

    public record RefreshRequest(
        @JsonProperty("refresh_token") String refreshToken
    ) {}
}