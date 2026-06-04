package com.cambofreelance.apigateway.controllers;

import com.cambofreelance.apigateway.configs.AdminAuthHelper;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.constants.Permissions;
import com.cambofreelance.apigateway.dto.DeprecateRouteRequest;
import com.cambofreelance.apigateway.dto.RejectRouteRequest;
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
@RequestMapping("/api/management/admin/routes")
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
            .then(apiRouteService.create(request, actor(exchange))
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r)));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<RouteApiResponse>> update(
            @PathVariable Long id, @RequestBody RouteApiRequest request, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.update(id, request, actor(exchange))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.<RouteApiResponse>notFound().build()));
    }

    @PutMapping("/{id}/enable")
    public Mono<ResponseEntity<Map<String, Object>>> enable(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.enable(id, actor(exchange))
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of("id", id, "status", "ACT", "message", "Route enabled"))));
    }

    @PutMapping("/{id}/disable")
    public Mono<ResponseEntity<Map<String, Object>>> disable(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.disable(id, actor(exchange))
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of("id", id, "status", "INACT", "message", "Route disabled"))));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.delete(id, actor(exchange))
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of("id", id, "message", "Route deleted"))));
    }

    @PostMapping("/{id}/submit")
    public Mono<ResponseEntity<Map<String, Object>>> submit(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.submit(id, actor(exchange))
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                    "id", id, "status", Constants.STATUS_PENDING,
                    "message", "Route submitted for approval"))));
    }

    @PostMapping("/{id}/approve")
    public Mono<ResponseEntity<Map<String, Object>>> approve(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_APPROVE)
            .then(apiRouteService.approve(id, actor(exchange))
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                    "id", id, "status", Constants.STATUS_ACTIVE,
                    "message", "Route approved and live"))));
    }

    @PostMapping("/{id}/reject")
    public Mono<ResponseEntity<Map<String, Object>>> reject(
            @PathVariable Long id, @RequestBody RejectRouteRequest request, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_APPROVE)
            .then(apiRouteService.reject(id, request.reason(), actor(exchange))
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                    "id", id, "status", Constants.STATUS_DRAFT,
                    "message", "Route rejected"))));
    }

    @PutMapping("/{id}/deprecate")
    public Mono<ResponseEntity<Map<String, Object>>> deprecate(
            @PathVariable Long id, @RequestBody DeprecateRouteRequest request, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.deprecate(id, request.sunsetDate(), actor(exchange))
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                    "id", id,
                    "status", Constants.STATUS_DEPRECATED,
                    "sunset_date", request.sunsetDate().toString(),
                    "message", "Route deprecated — will be retired after sunset_date"))));
    }

    @PutMapping("/{id}/undeprecate")
    public Mono<ResponseEntity<Map<String, Object>>> undeprecate(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.undeprecate(id, actor(exchange))
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                    "id", id,
                    "status", Constants.STATUS_ACTIVE,
                    "message", "Route restored to active"))));
    }

    @PutMapping("/{id}/retire")
    public Mono<ResponseEntity<Map<String, Object>>> retire(@PathVariable Long id, ServerWebExchange exchange) {
        return adminAuth.require(exchange, Permissions.ROUTE_WRITE)
            .then(apiRouteService.retire(id, actor(exchange))
                .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
                    "id", id,
                    "status", Constants.STATUS_RETIRED,
                    "message", "Route retired and removed from gateway"))));
    }

    private String actor(ServerWebExchange exchange) {
        String actor = exchange.getAttribute("adminUser");
        return actor != null ? actor : "system";
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
