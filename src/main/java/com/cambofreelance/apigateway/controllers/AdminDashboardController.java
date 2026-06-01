package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.caches.ServiceInstanceCache;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.repositories.ApiGroupRouteRepository;
import com.cambofreelance.apigateway.repositories.ApiRouteRepository;
import com.cambofreelance.apigateway.repositories.ServiceNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final ApiRouteRepository       apiRouteRepository;
    private final ApiGroupRouteRepository  groupRouteRepository;
    private final ServiceNodeRepository    serviceNodeRepository;

    /**
     * Returns a summary snapshot used by the dashboard home page:
     * - Route counts (active / inactive)
     * - Service group count
     * - Service instance health summary (per group)
     */
    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> summary() {
        Mono<Long> activeRoutes   = apiRouteRepository.countByStatus(Constants.STATUS_ACTIVE);
        Mono<Long> inactiveRoutes = apiRouteRepository.countByStatus("INACT");
        Mono<Long> groupCount     = groupRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE).count();

        // Instance health from in-memory cache (no DB round-trip)
        Map<String, Map<String, Long>> instanceHealth = ServiceInstanceCache.getServiceIds()
            .stream()
            .collect(Collectors.toMap(
                serviceId -> serviceId,
                serviceId -> {
                    var nodes = ServiceInstanceCache.getInstances(serviceId);
                    long up  = nodes.stream().filter(n -> "UP".equalsIgnoreCase(n.getHealthStatus())).count();
                    long down = nodes.size() - up;
                    Map<String, Long> health = new HashMap<>();
                    health.put("total", (long) nodes.size());
                    health.put("up",    up);
                    health.put("down",  down);
                    return health;
                }
            ));

        return Mono.zip(activeRoutes, inactiveRoutes, groupCount)
            .map(tuple -> {
                Map<String, Object> result = new HashMap<>();
                result.put("routes", Map.of(
                    "active",   tuple.getT1(),
                    "inactive", tuple.getT2(),
                    "total",    tuple.getT1() + tuple.getT2()
                ));
                result.put("serviceGroups", tuple.getT3());
                result.put("serviceInstances", instanceHealth);
                result.put("timestamp", System.currentTimeMillis());
                return ResponseEntity.ok(result);
            });
    }

    /**
     * Returns per-group route statistics for the Route Status panel.
     */
    @GetMapping("/route-status")
    public Mono<ResponseEntity<Map<String, Object>>> routeStatus() {
        return apiRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .collectMultimap(route -> route.getGroupCode() != null ? route.getGroupCode() : "UNGROUPED")
            .map(grouped -> {
                Map<String, Object> perGroup = new HashMap<>();
                grouped.forEach((groupCode, routes) -> perGroup.put(groupCode, Map.of(
                    "count",   routes.size(),
                    "methods", routes.stream()
                        .map(r -> r.getMethod() != null ? r.getMethod() : "ANY")
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList())
                )));
                return ResponseEntity.ok(Map.<String, Object>of(
                    "groups", perGroup,
                    "timestamp", System.currentTimeMillis()
                ));
            });
    }

    /**
     * Returns active service groups and their discovery configuration.
     */
    @GetMapping("/service-registry")
    public Mono<ResponseEntity<Object>> serviceRegistry() {
        return groupRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .map(group -> {
                boolean lbEnabled = group.getUri() != null && group.getUri().startsWith("lb://");
                var nodes         = ServiceInstanceCache.getInstances(group.getCode());
                long up   = nodes.stream().filter(n -> "UP".equalsIgnoreCase(n.getHealthStatus())).count();

                Map<String, Object> entry = new HashMap<>();
                entry.put("code",        group.getCode());
                entry.put("uri",         group.getUri());
                entry.put("lbEnabled",   lbEnabled);
                entry.put("instances",   nodes.size());
                entry.put("up",          up);
                return entry;
            })
            .collectList()
            .map(list -> ResponseEntity.ok((Object) list));
    }
}