package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.dto.ServiceNodeDto;
import com.cambofreelance.apigateway.models.ServiceNode;
import com.cambofreelance.apigateway.repositories.ServiceNodeRepository;
import com.cambofreelance.apigateway.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/health")
@RequiredArgsConstructor
public class InstanceHealthController {

    private static final Set<String> VALID_HEALTH_STATUSES =
        Set.of("UP", "DOWN", "OUT_OF_SERVICE");

    private final ServiceNodeRepository serviceNodeRepository;
    private final HealthCheckService healthCheckService;

    // ── GET /admin/health/instances ──────────────────────────────────────────

    /**
     * Lists all active service instances.
     * Optional ?serviceId=AUTH filter to scope to one service.
     */
    @GetMapping("/instances")
    public Mono<ResponseEntity<Map<String, Object>>> listInstances(
            @RequestParam(required = false) String serviceId) {

        return serviceNodeRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .filter(n -> serviceId == null || serviceId.equalsIgnoreCase(n.getServiceId()))
            .map(ServiceNodeDto::from)
            .collectList()
            .map(instances -> ResponseEntity.ok(Map.of(
                "total", instances.size(),
                "up",    instances.stream().filter(i -> "UP".equals(i.getHealthStatus())).count(),
                "down",  instances.stream().filter(i -> "DOWN".equals(i.getHealthStatus())).count(),
                "instances", instances
            )));
    }

    // ── GET /admin/health/instances/{id} ─────────────────────────────────────

    @GetMapping("/instances/{id}")
    public Mono<ResponseEntity<ServiceNodeDto>> getInstance(@PathVariable Long id) {
        return serviceNodeRepository.findById(id)
            .map(node -> ResponseEntity.ok(ServiceNodeDto.from(node)))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ── POST /admin/health/check ─────────────────────────────────────────────

    /**
     * Triggers an immediate health-check cycle across all active instances
     * and returns the refreshed results.
     */
    @PostMapping("/check")
    public Mono<ResponseEntity<Map<String, Object>>> triggerCheck() {
        return healthCheckService.checkNow()
            .map(nodes -> {
                List<ServiceNodeDto> dtos = nodes.stream()
                    .map(ServiceNodeDto::from)
                    .collect(Collectors.toList());
                long up = dtos.stream().filter(d -> "UP".equals(d.getHealthStatus())).count();
                return ResponseEntity.ok(Map.of(
                    "message",   "Health check completed",
                    "total",     dtos.size(),
                    "up",        up,
                    "down",      dtos.size() - up,
                    "instances", dtos
                ));
            });
    }

    // ── PUT /admin/health/instances/{id}/status ───────────────────────────────

    /**
     * Manually overrides the health status of a single instance.
     * Useful for draining an instance (OUT_OF_SERVICE) or restoring it (UP).
     * Body: { "healthStatus": "UP" | "DOWN" | "OUT_OF_SERVICE" }
     */
    @PutMapping("/instances/{id}/status")
    public Mono<ResponseEntity<ServiceNodeDto>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

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
                    .defaultIfEmpty(ResponseEntity.notFound().build());
            });
    }
}