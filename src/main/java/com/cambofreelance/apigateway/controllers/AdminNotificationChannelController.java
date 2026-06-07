package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.constants.Permissions;
import com.cambofreelance.apigateway.models.NotificationChannel;
import com.cambofreelance.apigateway.repositories.NotificationChannelRepository;
import com.cambofreelance.apigateway.service.impl.AlertNotificationService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/management/admin/notifications/channels")
@RequiredArgsConstructor
public class AdminNotificationChannelController {

    private static final Set<String> VALID_TYPES = Set.of(
        NotificationChannel.TYPE_EMAIL, NotificationChannel.TYPE_TELEGRAM, NotificationChannel.TYPE_SLACK);

    record ChannelRequest(
        String                          name,
        String                          type,
        String                          config,
        Boolean                         enabled,
        @JsonProperty("min_severity")   String  minSeverity,
        @JsonProperty("on_open")        Boolean onOpen,
        @JsonProperty("on_acknowledge") Boolean onAcknowledge,
        @JsonProperty("on_resolve")     Boolean onResolve
    ) {}

    private final NotificationChannelRepository channelRepository;
    private final AlertNotificationService       alertNotificationService;
    private final AdminAuthHelper                adminAuth;

    // ── List ───────────────────────────────────────────────────────────────────

    @GetMapping
    public Mono<ResponseEntity<List<NotificationChannel>>> list(ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.NOTIFICATION_READ)
            .then(channelRepository.findAllByStatus(Constants.STATUS_ACTIVE)
                .collectList()
                .map(ResponseEntity::ok));
    }

    // ── Get one ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public Mono<ResponseEntity<NotificationChannel>> getById(
            @PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.NOTIFICATION_READ)
            .then(channelRepository.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.<NotificationChannel>notFound().build()));
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @PostMapping
    public Mono<ResponseEntity<NotificationChannel>> create(
            @RequestBody ChannelRequest req, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.NOTIFICATION_WRITE).then(Mono.defer(() -> {
            if (req.name() == null || req.type() == null || req.config() == null) {
                return Mono.just(ResponseEntity.<NotificationChannel>badRequest().build());
            }
            if (!VALID_TYPES.contains(req.type().toUpperCase())) {
                return Mono.just(ResponseEntity.<NotificationChannel>badRequest().build());
            }
            LocalDateTime now = LocalDateTime.now();
            NotificationChannel channel = NotificationChannel.builder()
                .name(req.name())
                .type(req.type().toUpperCase())
                .config(req.config())
                .enabled(Boolean.TRUE.equals(req.enabled() == null || req.enabled()))
                .minSeverity(req.minSeverity())
                .onOpen(req.onOpen() == null || Boolean.TRUE.equals(req.onOpen()))
                .onAcknowledge(Boolean.TRUE.equals(req.onAcknowledge()))
                .onResolve(req.onResolve() == null || Boolean.TRUE.equals(req.onResolve()))
                .status(Constants.STATUS_ACTIVE)
                .createdAt(now)
                .createdBy(adminAuth.currentUser(exchange))
                .build();
            return channelRepository.save(channel)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
        }));
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public Mono<ResponseEntity<?>> update(
            @PathVariable Long id, @RequestBody ChannelRequest req, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.NOTIFICATION_WRITE)
            .then(channelRepository.findById(id)
                .flatMap(existing -> {
                    LocalDateTime now = LocalDateTime.now();
                    String type = req.type() != null ? req.type().toUpperCase() : existing.getType();
                    if (!VALID_TYPES.contains(type))
                        return Mono.just(ResponseEntity.<NotificationChannel>status(HttpStatus.BAD_REQUEST).build());

                    return channelRepository.updateChannel(
                        id,
                        req.name()          != null ? req.name()          : existing.getName(),
                        type,
                        req.config()        != null ? req.config()        : existing.getConfig(),
                        req.enabled()       != null ? req.enabled()       : existing.isEnabled(),
                        req.minSeverity()   != null ? req.minSeverity()   : existing.getMinSeverity(),
                        req.onOpen()        != null ? req.onOpen()        : existing.isOnOpen(),
                        req.onAcknowledge() != null ? req.onAcknowledge() : existing.isOnAcknowledge(),
                        req.onResolve()     != null ? req.onResolve()     : existing.isOnResolve(),
                        now, adminAuth.currentUser(exchange)
                    )
                    .flatMap(rows -> {
                        if (rows <= 0)
                            return Mono.just(ResponseEntity.<NotificationChannel>status(HttpStatus.NOT_FOUND).build());
                        return channelRepository.findById(id)
                            .map(ResponseEntity::ok);
                    });
                })
                .defaultIfEmpty(ResponseEntity.<NotificationChannel>status(HttpStatus.NOT_FOUND).build()));
    }

    // ── Delete (soft) ──────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(
            @PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.NOTIFICATION_WRITE)
            .then(channelRepository.softDelete(id, LocalDateTime.now(), adminAuth.currentUser(exchange))
                .map(rows -> rows > 0
                    ? ResponseEntity.ok(Map.<String, Object>of("id", id, "message", "Channel deleted"))
                    : ResponseEntity.<Map<String, Object>>notFound().build()));
    }

    // ── Test ───────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/test")
    public Mono<ResponseEntity<Map<String, Object>>> test(
            @PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.NOTIFICATION_WRITE)
            .then(channelRepository.findById(id)
                .flatMap(alertNotificationService::testChannel)
                .map(msg -> ResponseEntity.ok(Map.<String, Object>of("message", msg)))
                .defaultIfEmpty(ResponseEntity.<Map<String, Object>>notFound().build()));
    }
}
