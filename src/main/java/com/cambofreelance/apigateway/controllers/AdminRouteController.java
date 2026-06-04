package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Permissions;
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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminRouteController {

    private final ApiRouteService apiRouteService;
    private final AdminAuthHelper adminAuth;

    @GetMapping
    public Mono<ResponseEntity<List<RouteApiResponse>>> list(
            @RequestParam(defaultValue = "ACT") String status, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_READ)
            .then(apiRouteService.findAllByStatus(status).collectList().map(ResponseEntity::ok));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<RouteApiResponse>> getById(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_READ)
            .then(apiRouteService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.<RouteApiResponse>notFound().build()));
    }

    @PostMapping
    public Mono<ResponseEntity<RouteApiResponse>> create(@RequestBody RouteApiRequest request, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.create(request)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r)));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<RouteApiResponse>> update(
            @PathVariable Long id, @RequestBody RouteApiRequest request, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.update(id, request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.<RouteApiResponse>notFound().build()));
    }

    @PutMapping("/{id}/enable")
    public Mono<ResponseEntity<Map<String, Object>>> enable(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.enable(id)
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of("id", id, "status", "ACT", "message", "Route enabled"))));
    }

    @PutMapping("/{id}/disable")
    public Mono<ResponseEntity<Map<String, Object>>> disable(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.disable(id)
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of("id", id, "status", "INACT", "message", "Route disabled"))));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.delete(id)
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of("id", id, "message", "Route deleted"))));
    }

    @PostMapping("/reload")
    public Mono<ResponseEntity<Map<String, Object>>> reload(ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.reloadRoutes()
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                    "message", "Gateway routes reloaded successfully",
                    "timestamp", System.currentTimeMillis()))));
    }
}
