package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.constants.Permissions;
import com.cambofreelance.apigateway.models.ApiGroupRoute;
import com.cambofreelance.apigateway.repositories.ApiGroupRouteRepository;
import com.cambofreelance.apigateway.service.impl.GatewayRouteService;
import com.cambofreelance.apigateway.utils.SsrfGuard;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/management/admin/groups")
@RequiredArgsConstructor
public class AdminGroupController {

    private final ApiGroupRouteRepository groupRouteRepository;
    private final GatewayRouteService gatewayRouteService;
    private final AdminAuthHelper adminAuth;

    record BlueGreenConfig(
        @JsonProperty("blue_uri")  String blueUri,
        @JsonProperty("green_uri") String greenUri
    ) {}

    record BlueGreenStatus(
        String code,
        @JsonProperty("blue_uri")    String blueUri,
        @JsonProperty("green_uri")   String greenUri,
        @JsonProperty("active_slot") String activeSlot,
        @JsonProperty("live_uri")    String liveUri
    ) {}

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    public Mono<ResponseEntity<List<ApiGroupRoute>>> list(ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.GROUP_READ)
            .then(groupRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE)
                .collectList()
                .map(ResponseEntity::ok));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiGroupRoute>> getById(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.GROUP_READ)
            .then(groupRouteRepository.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.<ApiGroupRoute>notFound().build()));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public Mono<ResponseEntity<ApiGroupRoute>> create(@RequestBody ApiGroupRoute request, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.GROUP_WRITE)
            .then(Mono.defer(() -> {
                try {
                    if (request.getUri()      != null) SsrfGuard.assertSafeUri(request.getUri());
                    if (request.getBlueUri()  != null) SsrfGuard.assertSafeUri(request.getBlueUri());
                    if (request.getGreenUri() != null) SsrfGuard.assertSafeUri(request.getGreenUri());
                } catch (IllegalArgumentException e) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid upstream URI: " + e.getMessage()));
                }
                return groupRouteRepository.existsByCode(request.getCode())
                    .flatMap(exists -> {
                        if (Boolean.TRUE.equals(exists)) {
                            return Mono.<ResponseEntity<ApiGroupRoute>>just(
                                ResponseEntity.status(HttpStatus.CONFLICT).build());
                        }
                        request.setId(null);
                        request.setStatus(Constants.STATUS_ACTIVE);
                        request.setCreatedAt(LocalDateTime.now());
                        request.setCreatedBy(adminAuth.currentUser(exchange));
                        return groupRouteRepository.save(request)
                            .map(g -> ResponseEntity.status(HttpStatus.CREATED).body(g));
                    });
            }));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiGroupRoute>> update(
            @PathVariable Long id, @RequestBody ApiGroupRoute request, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.GROUP_WRITE)
            .then(Mono.defer(() -> {
                try {
                    if (request.getUri() != null) SsrfGuard.assertSafeUri(request.getUri());
                } catch (IllegalArgumentException e) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid upstream URI: " + e.getMessage()));
                }
                return groupRouteRepository.findById(id)
                    .switchIfEmpty(Mono.error(new RuntimeException("Group not found: " + id)))
                    .flatMap(existing -> {
                        if (request.getUri()  != null) existing.setUri(request.getUri());
                        if (request.getCode() != null) existing.setCode(request.getCode());
                        existing.setUpdatedAt(LocalDateTime.now());
                        existing.setUpdatedBy(adminAuth.currentUser(exchange));
                        return groupRouteRepository.save(existing);
                    })
                    .map(ResponseEntity::ok)
                    .defaultIfEmpty(ResponseEntity.<ApiGroupRoute>notFound().build());
            }));
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.GROUP_WRITE)
            .then(groupRouteRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Group not found: " + id)))
                .flatMap(g -> {
                    g.setStatus("INACT");
                    g.setUpdatedAt(LocalDateTime.now());
                    g.setUpdatedBy(adminAuth.currentUser(exchange));
                    return groupRouteRepository.save(g);
                })
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                    "id", id, "message", "Service group deleted"))));
    }

    // ── Blue-Green ────────────────────────────────────────────────────────────

    @GetMapping("/{code}/blue-green")
    public Mono<ResponseEntity<BlueGreenStatus>> blueGreenStatus(
            @PathVariable String code, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.GROUP_READ)
            .then(groupRouteRepository.findByCode(code)
                .map(g -> ResponseEntity.ok(new BlueGreenStatus(
                    g.getCode(), g.getBlueUri(), g.getGreenUri(),
                    g.getActiveSlot() != null ? g.getActiveSlot() : "BLUE",
                    g.resolvedUri())))
                .defaultIfEmpty(ResponseEntity.<BlueGreenStatus>notFound().build()));
    }

    @PutMapping("/{code}/blue-green")
    public Mono<ResponseEntity<BlueGreenStatus>> configureBlueGreen(
            @PathVariable String code, @RequestBody BlueGreenConfig req, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.GROUP_WRITE)
            .then(Mono.defer(() -> {
                try {
                    if (req.blueUri()  != null) SsrfGuard.assertSafeUri(req.blueUri());
                    if (req.greenUri() != null) SsrfGuard.assertSafeUri(req.greenUri());
                } catch (IllegalArgumentException e) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid upstream URI: " + e.getMessage()));
                }
                return groupRouteRepository.configureSlots(
                        code, req.blueUri(), req.greenUri(),
                        LocalDateTime.now(), adminAuth.currentUser(exchange))
                    .filter(rows -> rows > 0)
                    .switchIfEmpty(Mono.error(new RuntimeException("Group not found: " + code)))
                    .flatMap(r -> groupRouteRepository.findByCode(code))
                    .map(g -> {
                        gatewayRouteService.refreshRoutes();
                        return ResponseEntity.ok(new BlueGreenStatus(
                            g.getCode(), g.getBlueUri(), g.getGreenUri(),
                            g.getActiveSlot() != null ? g.getActiveSlot() : "BLUE",
                            g.resolvedUri()));
                    });
            }));
    }

    @PostMapping("/{code}/swap")
    public Mono<ResponseEntity<BlueGreenStatus>> swapSlot(
            @PathVariable String code, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.GROUP_WRITE)
            .then(groupRouteRepository.swapSlot(
                    code, LocalDateTime.now(), adminAuth.currentUser(exchange))
                .filter(rows -> rows > 0)
                .switchIfEmpty(Mono.error(new RuntimeException("Group not found: " + code)))
                .flatMap(r -> groupRouteRepository.findByCode(code))
                .map(g -> {
                    gatewayRouteService.refreshRoutes();
                    return ResponseEntity.ok(new BlueGreenStatus(
                        g.getCode(), g.getBlueUri(), g.getGreenUri(),
                        g.getActiveSlot(),
                        g.resolvedUri()));
                }));
    }
}
