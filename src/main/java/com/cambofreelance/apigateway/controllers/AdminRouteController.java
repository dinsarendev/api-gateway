package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.dto.RouteApiRequest;
import com.cambofreelance.apigateway.dto.RouteApiResponse;
import com.cambofreelance.apigateway.service.ApiRouteService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminRouteController {

    private final ApiRouteService apiRouteService;

    // ── List ──────────────────────────────────────────────────────────────────

    /**
     * GET /admin/routes          → all ACT routes
     * GET /admin/routes?status=INACT → all INACT routes
     */
    @GetMapping
    public Mono<ResponseEntity<List<RouteApiResponse>>> list(
            @RequestParam(defaultValue = "ACT") String status) {
        return apiRouteService.findAllByStatus(status)
            .collectList()
            .map(ResponseEntity::ok);
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public Mono<ResponseEntity<RouteApiResponse>> getById(@PathVariable Long id) {
        return apiRouteService.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public Mono<ResponseEntity<RouteApiResponse>> create(@RequestBody RouteApiRequest request) {
        return apiRouteService.create(request)
            .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public Mono<ResponseEntity<RouteApiResponse>> update(
            @PathVariable Long id,
            @RequestBody RouteApiRequest request) {
        return apiRouteService.update(id, request)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ── Enable / Disable ──────────────────────────────────────────────────────

    @PutMapping("/{id}/enable")
    public Mono<ResponseEntity<Map<String, Object>>> enable(@PathVariable Long id) {
        return apiRouteService.enable(id)
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                "id", id, "status", "ACT", "message", "Route enabled")));
    }

    @PutMapping("/{id}/disable")
    public Mono<ResponseEntity<Map<String, Object>>> disable(@PathVariable Long id) {
        return apiRouteService.disable(id)
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                "id", id, "status", "INACT", "message", "Route disabled")));
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable Long id) {
        return apiRouteService.delete(id)
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                "id", id, "message", "Route deleted")));
    }

    // ── Reload ────────────────────────────────────────────────────────────────

    /**
     * Forces a full reload of Spring Cloud Gateway routes + local caches.
     * Use after bulk DB changes or configuration pushes.
     */
    @PostMapping("/reload")
    public Mono<ResponseEntity<Map<String, Object>>> reload() {
        return apiRouteService.reloadRoutes()
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                "message", "Gateway routes reloaded successfully",
                "timestamp", System.currentTimeMillis())));
    }
}