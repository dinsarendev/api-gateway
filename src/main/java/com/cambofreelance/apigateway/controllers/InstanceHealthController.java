package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.constants.Permissions;
import com.cambofreelance.apigateway.dto.ServiceNodeDto;
import com.cambofreelance.apigateway.models.ServiceNode;
import com.cambofreelance.apigateway.repositories.ServiceNodeRepository;
import com.cambofreelance.apigateway.service.HealthCheckService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/management/admin/health")
@RequiredArgsConstructor
public class InstanceHealthController {

    private static final Set<String> VALID_HEALTH_STATUSES = Set.of("UP", "DOWN", "OUT_OF_SERVICE");

    record InstanceRequest(
        @JsonProperty("service_id")  String  serviceId,
        String                               host,
        Integer                              port,
        Boolean                              secure,
        Integer                              weight,
        @JsonProperty("health_path") String  healthPath
    ) {}

    private final ServiceNodeRepository serviceNodeRepository;
    private final HealthCheckService    healthCheckService;
    private final AdminAuthHelper       adminAuth;

    // ── GET /admin/health/instances ──────────────────────────────────────────

    @GetMapping("/instances")
    public Mono<ResponseEntity<Map<String, Object>>> listInstances(
            @RequestParam(required = false) String serviceId, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.HEALTH_READ)
            .then(serviceNodeRepository.findAllByStatus(Constants.STATUS_ACTIVE)
                .filter(n -> serviceId == null || serviceId.equalsIgnoreCase(n.getServiceId()))
                .map(ServiceNodeDto::from)
                .collectList()
                .map(instances -> ResponseEntity.ok(Map.of(
                    "total",     instances.size(),
                    "up",        instances.stream().filter(i -> "UP".equals(i.getHealthStatus())).count(),
                    "down",      instances.stream().filter(i -> "DOWN".equals(i.getHealthStatus())).count(),
                    "instances", instances
                ))));
    }

    @GetMapping("/instances/{id}")
    public Mono<ResponseEntity<ServiceNodeDto>> getInstance(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.HEALTH_READ)
            .then(serviceNodeRepository.findById(id)
                .map(node -> ResponseEntity.ok(ServiceNodeDto.from(node)))
                .defaultIfEmpty(ResponseEntity.<ServiceNodeDto>notFound().build()));
    }

    // ── POST /admin/health/check ─────────────────────────────────────────────

    @PostMapping("/check")
    public Mono<ResponseEntity<Map<String, Object>>> triggerCheck(ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.HEALTH_READ)
            .then(healthCheckService.checkNow()
                .map(nodes -> {
                    List<ServiceNodeDto> dtos = nodes.stream().map(ServiceNodeDto::from).collect(Collectors.toList());
                    long up = dtos.stream().filter(d -> "UP".equals(d.getHealthStatus())).count();
                    return ResponseEntity.ok(Map.of(
                        "message", "Health check completed",
                        "total", dtos.size(), "up", up, "down", dtos.size() - up,
                        "instances", dtos
                    ));
                }));
    }

    // ── POST /admin/health/instances ─────────────────────────────────────────

    @PostMapping("/instances")
    public Mono<ResponseEntity<ServiceNodeDto>> createInstance(
            @RequestBody InstanceRequest req, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.REGISTRY_WRITE).then(Mono.defer(() -> {
            if (req.serviceId() == null || req.host() == null || req.port() == null) {
                return Mono.just(ResponseEntity.<ServiceNodeDto>badRequest().build());
            }
            LocalDateTime now = LocalDateTime.now();
            ServiceNode node = new ServiceNode();
            node.setServiceId(req.serviceId().toUpperCase());
            node.setHost(req.host());
            node.setPort(req.port());
            node.setSecure(Boolean.TRUE.equals(req.secure()));
            node.setWeight(req.weight() != null ? req.weight() : 1);
            node.setHealthPath(req.healthPath());
            node.setHealthStatus("UP");
            node.setStatus(Constants.STATUS_ACTIVE);
            node.setCreatedAt(now);
            node.setCreatedBy(adminAuth.currentUser(exchange));
            return serviceNodeRepository.save(node)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(ServiceNodeDto.from(saved)))
                .doOnSuccess(r -> healthCheckService.refreshCache().subscribe());
        }));
    }

    // ── PUT /admin/health/instances/{id} ─────────────────────────────────────

    @PutMapping("/instances/{id}")
    public Mono<ResponseEntity<ServiceNodeDto>> updateInstance(
            @PathVariable Long id, @RequestBody InstanceRequest req, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.REGISTRY_WRITE)
            .then(serviceNodeRepository.findById(id)
                .flatMap(existing -> serviceNodeRepository.updateInstance(
                    id,
                    req.serviceId()  != null ? req.serviceId().toUpperCase() : existing.getServiceId(),
                    req.host()       != null ? req.host()       : existing.getHost(),
                    req.port()       != null ? req.port()       : existing.getPort(),
                    req.secure()     != null ? req.secure()     : existing.isSecure(),
                    req.weight()     != null ? req.weight()     : existing.getWeight(),
                    req.healthPath() != null ? req.healthPath() : existing.getHealthPath(),
                    LocalDateTime.now(), adminAuth.currentUser(exchange)
                ))
                .flatMap(rows -> rows > 0
                    ? serviceNodeRepository.findById(id).map(n -> ResponseEntity.ok(ServiceNodeDto.from(n)))
                    : Mono.<ResponseEntity<ServiceNodeDto>>just(ResponseEntity.<ServiceNodeDto>notFound().build()))
                .defaultIfEmpty(ResponseEntity.<ServiceNodeDto>notFound().build())
                .doOnSuccess(r -> healthCheckService.refreshCache().subscribe()));
    }

    // ── DELETE /admin/health/instances/{id} ───────────────────────────────────

    @DeleteMapping("/instances/{id}")
    public Mono<ResponseEntity<Void>> deleteInstance(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.REGISTRY_WRITE)
            .then(serviceNodeRepository.softDelete(id, LocalDateTime.now(), adminAuth.currentUser(exchange))
                .flatMap(rows -> rows > 0
                    ? healthCheckService.refreshCache().thenReturn(ResponseEntity.<Void>noContent().build())
                    : Mono.<ResponseEntity<Void>>just(ResponseEntity.notFound().build())));
    }

    // ── PUT /admin/health/instances/{id}/status ───────────────────────────────

    @PutMapping("/instances/{id}/status")
    public Mono<ResponseEntity<ServiceNodeDto>> updateStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.HEALTH_READ).then(Mono.defer(() -> {
            String requested = body.getOrDefault("healthStatus", "").toUpperCase();
            if (!VALID_HEALTH_STATUSES.contains(requested)) {
                return Mono.just(ResponseEntity.badRequest().<ServiceNodeDto>build());
            }
            LocalDateTime now = LocalDateTime.now();
            return serviceNodeRepository.updateHealthStatus(id, requested, now, now)
                .flatMap(updated -> {
                    if (updated == 0) return Mono.just(ResponseEntity.notFound().<ServiceNodeDto>build());
                    return healthCheckService.refreshCache()
                        .then(serviceNodeRepository.findById(id))
                        .map(node -> ResponseEntity.ok(ServiceNodeDto.from(node)))
                        .defaultIfEmpty(ResponseEntity.<ServiceNodeDto>notFound().build());
                });
        }));
    }
}
