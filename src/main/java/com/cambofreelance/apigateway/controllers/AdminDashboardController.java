package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.caches.ServiceInstanceCache;
import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.repositories.AdminUserRepository;
import com.cambofreelance.apigateway.repositories.ApiGroupRouteRepository;
import com.cambofreelance.apigateway.repositories.ApiKeyRepository;
import com.cambofreelance.apigateway.repositories.ApiRouteRepository;
import com.cambofreelance.apigateway.repositories.ServiceNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/management/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final ApiRouteRepository      apiRouteRepository;
    private final ApiGroupRouteRepository groupRouteRepository;
    private final ServiceNodeRepository   serviceNodeRepository;
    private final ApiKeyRepository        apiKeyRepository;
    private final AdminUserRepository     adminUserRepository;
    private final AdminAuthHelper         adminAuth;

    /**
     * Main dashboard summary — routes, groups, instances, api_type breakdown,
     * API key count, admin user count.
     */
    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> summary(ServerWebExchange exchange) {
        Mono<Long> activeRoutes   = apiRouteRepository.countByStatus(Constants.STATUS_ACTIVE);
        Mono<Long> inactiveRoutes = apiRouteRepository.countByStatus("INACT");
        Mono<Long> groupCount     = groupRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE).count();
        Mono<Long> apiKeyCount    = apiKeyRepository.findAllByStatus(Constants.STATUS_ACTIVE).count();
        Mono<Long> userCount      = adminUserRepository.countByFilter(Constants.STATUS_ACTIVE, null);

        // api_type breakdown — count active routes per type from DB
        Mono<Map<String, Long>> apiTypeBreakdown = apiRouteRepository
            .findAllByStatus(Constants.STATUS_ACTIVE)
            .collect(Collectors.groupingBy(
                r -> r.getApiType() != null ? r.getApiType().toUpperCase() : "REST",
                Collectors.counting()
            ));

        // Instance health from in-memory cache
        Map<String, Map<String, Long>> instanceHealth = ServiceInstanceCache.getServiceIds()
            .stream()
            .collect(Collectors.toMap(
                sid -> sid,
                sid -> {
                    var nodes = ServiceInstanceCache.getInstances(sid);
                    long up   = nodes.stream().filter(n -> "UP".equalsIgnoreCase(n.getHealthStatus())).count();
                    Map<String, Long> h = new HashMap<>();
                    h.put("total", (long) nodes.size());
                    h.put("up",    up);
                    h.put("down",  nodes.size() - up);
                    return h;
                }
            ));

        long totalInstances = instanceHealth.values().stream().mapToLong(m -> m.getOrDefault("total", 0L)).sum();
        long totalUp        = instanceHealth.values().stream().mapToLong(m -> m.getOrDefault("up", 0L)).sum();

        return Mono.zip(activeRoutes, inactiveRoutes, groupCount, apiKeyCount, userCount, apiTypeBreakdown)
            .map(t -> {
                Map<String, Object> result = new HashMap<>();
                result.put("routes", Map.of(
                    "active",   t.getT1(),
                    "inactive", t.getT2(),
                    "total",    t.getT1() + t.getT2()
                ));
                result.put("serviceGroups",    t.getT3());
                result.put("apiKeys",          t.getT4());
                result.put("adminUsers",       t.getT5());
                result.put("apiTypeBreakdown", t.getT6());
                result.put("serviceInstances", instanceHealth);
                result.put("instancesSummary", Map.of(
                    "total", totalInstances,
                    "up",    totalUp,
                    "down",  totalInstances - totalUp
                ));
                result.put("timestamp", System.currentTimeMillis());
                return ResponseEntity.ok(result);
            });
    }

    /**
     * Per-group route statistics — count, methods, api_types used.
     */
    @GetMapping("/route-status")
    public Mono<ResponseEntity<Map<String, Object>>> routeStatus(ServerWebExchange exchange) {
        return apiRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .collectMultimap(r -> r.getGroupCode() != null ? r.getGroupCode() : "UNGROUPED")
            .map(grouped -> {
                Map<String, Object> perGroup = new HashMap<>();
                grouped.forEach((groupCode, routes) -> {
                    Map<String, Long> byType = routes.stream()
                        .collect(Collectors.groupingBy(
                            r -> r.getApiType() != null ? r.getApiType().toUpperCase() : "REST",
                            Collectors.counting()
                        ));
                    perGroup.put(groupCode, Map.of(
                        "count",    routes.size(),
                        "methods",  routes.stream()
                            .map(r -> r.getMethod() != null ? r.getMethod() : "ANY")
                            .distinct().sorted().collect(Collectors.toList()),
                        "apiTypes", byType
                    ));
                });
                return ResponseEntity.ok(Map.<String, Object>of(
                    "groups",    perGroup,
                    "timestamp", System.currentTimeMillis()
                ));
            });
    }

    /**
     * Active service groups with lb:// discovery info and live health counts.
     */
    @GetMapping("/service-registry")
    public Mono<ResponseEntity<Object>> serviceRegistry(ServerWebExchange exchange) {
        return groupRouteRepository.findAllByStatus(Constants.STATUS_ACTIVE)
            .map(group -> {
                boolean lbEnabled = group.getUri() != null && group.getUri().startsWith("lb://");
                var nodes         = ServiceInstanceCache.getInstances(group.getCode());
                long up           = nodes.stream().filter(n -> "UP".equalsIgnoreCase(n.getHealthStatus())).count();
                Map<String, Object> entry = new HashMap<>();
                entry.put("code",      group.getCode());
                entry.put("uri",       group.getUri());
                entry.put("lbEnabled", lbEnabled);
                entry.put("instances", nodes.size());
                entry.put("up",        up);
                return entry;
            })
            .collectList()
            .map(list -> ResponseEntity.ok((Object) list));
    }
}
