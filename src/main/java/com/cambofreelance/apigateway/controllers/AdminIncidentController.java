package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Permissions;
import com.cambofreelance.apigateway.models.AlertHistory;
import com.cambofreelance.apigateway.models.Incident;
import com.cambofreelance.apigateway.service.impl.IncidentService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/management/admin/incidents")
@RequiredArgsConstructor
public class AdminIncidentController {

    private final IncidentService incidentService;
    private final AdminAuthHelper adminAuth;

    record IncidentRequest(
        String                        title,
        String                        description,
        String                        severity,
        String                        type,
        @JsonProperty("affected_service") String affectedService,
        @JsonProperty("affected_route")   String affectedRoute
    ) {}

    record UpdateRequest(
        String status,
        String severity,
        String title,
        String description
    ) {}

    record AcknowledgeRequest(String note) {}

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public Mono<ResponseEntity<Map<String, Object>>> dashboard(ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.INCIDENT_READ)
            .then(incidentService.getDashboard().map(ResponseEntity::ok));
    }

    // ── List ───────────────────────────────────────────────────────────────────

    @GetMapping
    public Mono<ResponseEntity<List<Incident>>> list(
            @RequestParam(defaultValue = "ALL") String status,
            ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.INCIDENT_READ)
            .then(incidentService.list(status).collectList().map(ResponseEntity::ok));
    }

    // ── Get one ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Incident>> getById(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.INCIDENT_READ)
            .then(incidentService.getById(id).map(ResponseEntity::ok));
    }

    // ── Create manual ──────────────────────────────────────────────────────────

    @PostMapping
    public Mono<ResponseEntity<Incident>> create(
            @RequestBody IncidentRequest req, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.INCIDENT_WRITE)
            .then(Mono.defer(() -> {
                Incident incident = Incident.builder()
                    .title(req.title())
                    .description(req.description())
                    .severity(req.severity() != null ? req.severity() : Incident.SEV_HIGH)
                    .type(req.type() != null ? req.type() : Incident.TYPE_ERROR)
                    .affectedService(req.affectedService())
                    .affectedRoute(req.affectedRoute())
                    .build();
                return incidentService.create(incident, adminAuth.currentUser(exchange));
            }))
            .map(i -> ResponseEntity.status(HttpStatus.CREATED).body(i));
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Incident>> update(
            @PathVariable Long id, @RequestBody UpdateRequest req, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.INCIDENT_WRITE)
            .then(incidentService.update(
                id, req.status(), req.severity(), req.title(), req.description(),
                adminAuth.currentUser(exchange)))
            .map(ResponseEntity::ok);
    }

    // ── Acknowledge ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/acknowledge")
    public Mono<ResponseEntity<Incident>> acknowledge(
            @PathVariable Long id,
            @RequestBody(required = false) AcknowledgeRequest req,
            ServerWebExchange exchange) {
        String note = req != null ? req.note() : null;
        return adminAuth.require(exchange, Permissions.INCIDENT_WRITE)
            .then(incidentService.acknowledge(id, adminAuth.currentUser(exchange), note))
            .map(ResponseEntity::ok);
    }

    // ── Resolve ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/resolve")
    public Mono<ResponseEntity<Incident>> resolve(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.INCIDENT_WRITE)
            .then(incidentService.resolve(id, adminAuth.currentUser(exchange)))
            .map(ResponseEntity::ok);
    }

    // ── Alert History ──────────────────────────────────────────────────────────

    @GetMapping("/{id}/history")
    public Mono<ResponseEntity<List<AlertHistory>>> history(
            @PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.INCIDENT_READ)
            .then(incidentService.getHistory(id).collectList().map(ResponseEntity::ok));
    }

    // ── Close ──────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> close(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.INCIDENT_WRITE)
            .then(incidentService.close(id, adminAuth.currentUser(exchange)))
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of("id", id, "message", "Incident closed")));
    }
}
