package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Permissions;
import com.cambofreelance.apigateway.models.AdminUser;
import com.cambofreelance.apigateway.models.UserRole;
import com.cambofreelance.apigateway.repositories.AdminRoleRepository;
import com.cambofreelance.apigateway.repositories.AdminUserRepository;
import com.cambofreelance.apigateway.repositories.UserRoleRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/management/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserRepository userRepository;
    private final AdminRoleRepository roleRepository;
    private final UserRoleRepository  userRoleRepository;
    private final AdminAuthHelper     adminAuth;

    @GetMapping
    public Mono<ResponseEntity<PagedResponse>> list(
            @RequestParam(defaultValue = "ACT")  String status,
            @RequestParam(defaultValue = "")     String search,
            @RequestParam(defaultValue = "0")    int    page,
            @RequestParam(defaultValue = "15")   int    size,
            ServerWebExchange exchange) {

        String statusParam = status.isBlank() ? null : status;
        String searchParam = search.isBlank() ? null : "%" + search + "%";
        long   offset      = (long) page * size;

        return adminAuth.require(exchange, Permissions.USER_READ).then(Mono.defer(() -> {
            Mono<Long> countMono = userRepository.countByFilter(statusParam, searchParam);
            Mono<List<UserDto>> rowsMono = userRepository
                .findByFilter(statusParam, searchParam, size, offset)
                .flatMap(u -> roleRepository.findByUserId(u.getId())
                    .map(r -> r.getName()).collectList()
                    .map(roles -> toDto(u, roles)))
                .collectList();
            return Mono.zip(countMono, rowsMono)
                .map(t -> ResponseEntity.ok(new PagedResponse(t.getT2(), t.getT1(), page, size)));
        }));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<UserDto>> getById(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.USER_READ)
            .then(userRepository.findById(id)
                .flatMap(u -> roleRepository.findByUserId(u.getId())
                    .map(r -> r.getName()).collectList()
                    .map(roles -> ResponseEntity.ok(toDto(u, roles))))
                .defaultIfEmpty(ResponseEntity.<UserDto>notFound().build()));
    }

    @PostMapping
    public Mono<ResponseEntity<UserDto>> create(@RequestBody UserRequest req, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.USER_WRITE).then(Mono.defer(() -> {
            String actor = adminAuth.currentUser(exchange);
            return userRepository.findByUsername(req.username())
                .flatMap(existing -> Mono.<ResponseEntity<UserDto>>just(
                    ResponseEntity.status(HttpStatus.CONFLICT).build()))
                .switchIfEmpty(
                    Mono.fromCallable(() -> BCrypt.hashpw(req.password(), BCrypt.gensalt(12)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(hash -> {
                            AdminUser user = AdminUser.builder()
                                .username(req.username()).passwordHash(hash)
                                .email(req.email()).fullName(req.fullName())
                                .createdAt(LocalDateTime.now()).createdBy(actor).build();
                            return userRepository.save(user);
                        })
                        .flatMap(saved -> assignRoles(saved.getId(), req.roles())
                            .then(roleRepository.findByUserId(saved.getId())
                                .map(r -> r.getName()).collectList()
                                .map(roles -> ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved, roles))))));
        }));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<UserDto>> update(
            @PathVariable Long id, @RequestBody UserRequest req, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.USER_WRITE).then(Mono.defer(() -> {
            String actor = adminAuth.currentUser(exchange);
            return userRepository.findById(id)
                .flatMap(existing -> {
                    Mono<String> hashMono = (req.password() != null && !req.password().isBlank())
                        ? Mono.fromCallable(() -> BCrypt.hashpw(req.password(), BCrypt.gensalt(12)))
                              .subscribeOn(Schedulers.boundedElastic())
                        : Mono.just(existing.getPasswordHash());
                    return hashMono.flatMap(hash -> {
                        existing.setEmail(req.email());
                        existing.setFullName(req.fullName());
                        existing.setPasswordHash(hash);
                        existing.setUpdatedAt(LocalDateTime.now());
                        existing.setUpdatedBy(actor);
                        return userRepository.save(existing);
                    });
                })
                .flatMap(saved -> assignRoles(saved.getId(), req.roles())
                    .then(roleRepository.findByUserId(saved.getId())
                        .map(r -> r.getName()).collectList()
                        .map(roles -> ResponseEntity.ok(toDto(saved, roles)))))
                .defaultIfEmpty(ResponseEntity.<UserDto>notFound().build());
        }));
    }

    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<Map<String, Object>>> updateStatus(
            @PathVariable Long id, @RequestBody StatusRequest req, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.USER_WRITE).then(Mono.defer(() -> {
            String actor = adminAuth.currentUser(exchange);
            return userRepository.findById(id)
                .flatMap(u -> userRepository.updateStatus(id, req.status(), LocalDateTime.now(), actor))
                .map(rows -> ResponseEntity.ok(Map.<String, Object>of(
                    "id", id, "status", req.status(), "message", "User status updated")))
                .defaultIfEmpty(ResponseEntity.<Map<String, Object>>notFound().build());
        }));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.USER_WRITE).then(Mono.defer(() -> {
            String actor = adminAuth.currentUser(exchange);
            return userRepository.findById(id)
                .flatMap(u -> userRepository.updateStatus(id, "INACT", LocalDateTime.now(), actor))
                .map(rows -> ResponseEntity.ok(Map.<String, Object>of("id", id, "message", "User deactivated")))
                .defaultIfEmpty(ResponseEntity.<Map<String, Object>>notFound().build());
        }));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<Void> assignRoles(Long userId, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) return Mono.empty();
        return userRoleRepository.deleteByUserId(userId)
            .thenMany(roleRepository.findAllActive()
                .filter(r -> roleNames.contains(r.getName()))
                .flatMap(r -> userRoleRepository.save(
                    UserRole.builder().userId(userId).roleId(r.getId()).build())))
            .then();
    }

    private UserDto toDto(AdminUser u, List<String> roles) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
            u.getStatus(), u.getLastLoginAt(), u.getCreatedAt(), roles);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserDto(
        Long id, String username, String email,
        @JsonProperty("full_name")     String        fullName,
        String status,
        @JsonProperty("last_login_at") LocalDateTime lastLoginAt,
        @JsonProperty("created_at")    LocalDateTime createdAt,
        List<String> roles
    ) {}

    public record PagedResponse(List<UserDto> data, long total, int page, int size) {}

    public record UserRequest(
        String username, String password, String email,
        @JsonProperty("full_name") String fullName,
        List<String> roles
    ) {}

    public record StatusRequest(String status) {}
}
